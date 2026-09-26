package com.servicedna.alert.service;

import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.AlertRule;
import com.servicedna.alert.repository.AlertRuleRepository;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.repository.MaintenanceWindowRepository;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.telemetry.domain.ServicePing;
import com.servicedna.telemetry.repository.ServicePingRepository;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Evaluates threshold alert rules (LATENCY_ABOVE, ERROR_RATE_ABOVE, CONSECUTIVE_FAILURES) against
 * recent pings from both the active prober and push agents. Each rule fires once when its
 * threshold is crossed and once when it clears, tracked by {@link AlertRule#isBreached()}.
 */
@Component
public class AlertThresholdEvaluator {

  private static final Logger log = LoggerFactory.getLogger(AlertThresholdEvaluator.class);
  private static final List<AlertCondition> THRESHOLD_CONDITIONS =
      Arrays.stream(AlertCondition.values()).filter(AlertCondition::isThreshold).toList();

  private final AlertRuleRepository alertRuleRepository;
  private final ServicePingRepository servicePingRepository;
  private final ServiceRepository serviceRepository;
  private final MaintenanceWindowRepository maintenanceWindowRepository;
  private final AlertActionService actions;
  private final int minSamples;

  public AlertThresholdEvaluator(
      AlertRuleRepository alertRuleRepository,
      ServicePingRepository servicePingRepository,
      ServiceRepository serviceRepository,
      MaintenanceWindowRepository maintenanceWindowRepository,
      AlertActionService actions,
      @Value("${alert.threshold-min-samples:3}") int minSamples) {
    this.alertRuleRepository = alertRuleRepository;
    this.servicePingRepository = servicePingRepository;
    this.serviceRepository = serviceRepository;
    this.maintenanceWindowRepository = maintenanceWindowRepository;
    this.actions = actions;
    this.minSamples = Math.max(1, minSamples);
  }

  @Scheduled(fixedRateString = "${alert.threshold-evaluation-interval-ms:30000}")
  public void evaluateAll() {
    for (AlertRule rule : alertRuleRepository.findWithServiceByConditionIn(THRESHOLD_CONDITIONS)) {
      try {
        evaluate(rule, OffsetDateTime.now());
      } catch (Exception e) {
        log.warn("Evaluating alert rule {} failed: {}", rule.getId(), e.getMessage());
      }
    }
  }

  void evaluate(AlertRule rule, OffsetDateTime now) {
    UUID serviceId = rule.getService().getId();
    Double value = measure(rule, serviceId, now);
    if (value == null) {
      return; // not enough data to judge either way; keep the current state
    }

    boolean breached =
        rule.getCondition() == AlertCondition.CONSECUTIVE_FAILURES
            ? value >= rule.getThreshold()
            : value > rule.getThreshold();
    if (breached == rule.isBreached()) {
      return;
    }
    // Staying un-breached during maintenance means the rule fires once maintenance ends, if the
    // problem is still there, instead of never.
    if (breached && maintenanceWindowRepository.isServiceInActiveMaintenance(serviceId, now)) {
      return;
    }

    rule.setBreached(breached);
    alertRuleRepository.save(rule);

    UUID organizationId = rule.getOrganization().getId();
    String serviceName = rule.getService().getName();
    AlertActionService.AlertContext context =
        new AlertActionService.AlertContext(serviceName, null, value, rule.getThreshold());
    List<AlertRule> rules = List.of(rule);

    if (breached) {
      String message = breachMessage(rule, serviceName, value);
      actions.sendWebhooks(rules, message, context);
      actions.openIncident(rules, organizationId, serviceId, serviceName, headline(rule), message);
    } else {
      actions.sendWebhooks(rules, clearedMessage(rule, serviceName, value), context);
      boolean healthy =
          serviceRepository
              .findById(serviceId)
              .map(s -> s.getStatus() == ServiceStatus.HEALTHY)
              .orElse(false);
      if (healthy) {
        actions.resolveIncident(organizationId, serviceId, serviceName);
      }
    }
  }

  /** The rule's metric, or null when there are too few pings to judge. */
  private Double measure(AlertRule rule, UUID serviceId, OffsetDateTime now) {
    if (rule.getCondition() == AlertCondition.CONSECUTIVE_FAILURES) {
      int count = rule.getThreshold().intValue();
      List<ServicePing> latest =
          servicePingRepository.findByServiceIdOrderByCreatedAtDesc(
              serviceId, PageRequest.of(0, count));
      if (latest.size() < count) {
        return null;
      }
      return (double) latest.stream().takeWhile(p -> p.getStatus() == ServiceStatus.DOWN).count();
    }

    List<ServicePing> window =
        servicePingRepository.findByServiceIdAndCreatedAtAfterOrderByCreatedAtAsc(
            serviceId, now.minusMinutes(rule.getWindowMinutes()));
    if (rule.getCondition() == AlertCondition.ERROR_RATE_ABOVE) {
      if (window.size() < minSamples) {
        return null;
      }
      long failed = window.stream().filter(p -> p.getStatus() == ServiceStatus.DOWN).count();
      return failed * 100.0 / window.size();
    }

    // LATENCY_ABOVE: failed checks are excluded — a timeout's latency is the timeout, not the
    // service's response time, and failures are ERROR_RATE_ABOVE's job.
    List<Integer> latencies =
        window.stream()
            .filter(p -> p.getStatus() != ServiceStatus.DOWN && p.getLatencyMs() != null)
            .map(ServicePing::getLatencyMs)
            .toList();
    if (latencies.size() < minSamples) {
      return null;
    }
    return latencies.stream().mapToInt(Integer::intValue).average().orElseThrow();
  }

  private static String headline(AlertRule rule) {
    return switch (rule.getCondition()) {
      case LATENCY_ABOVE -> "has high latency";
      case ERROR_RATE_ABOVE -> "has a high error rate";
      case CONSECUTIVE_FAILURES -> "is failing repeatedly";
      default -> throw new IllegalArgumentException("Not a threshold condition: " + rule.getCondition());
    };
  }

  private static String breachMessage(AlertRule rule, String serviceName, double value) {
    return switch (rule.getCondition()) {
      case LATENCY_ABOVE ->
          String.format(
              "Service '%s' average latency %.0f ms over the last %d min is above %.0f ms",
              serviceName, value, rule.getWindowMinutes(), rule.getThreshold());
      case ERROR_RATE_ABOVE ->
          String.format(
              "Service '%s' error rate %.0f%% over the last %d min is above %.0f%%",
              serviceName, value, rule.getWindowMinutes(), rule.getThreshold());
      case CONSECUTIVE_FAILURES ->
          String.format(
              "Service '%s' failed its last %.0f health checks", serviceName, rule.getThreshold());
      default -> throw new IllegalArgumentException("Not a threshold condition: " + rule.getCondition());
    };
  }

  private static String clearedMessage(AlertRule rule, String serviceName, double value) {
    return switch (rule.getCondition()) {
      case LATENCY_ABOVE ->
          String.format(
              "Service '%s' average latency is back to %.0f ms (threshold %.0f ms)",
              serviceName, value, rule.getThreshold());
      case ERROR_RATE_ABOVE ->
          String.format(
              "Service '%s' error rate is back to %.0f%% (threshold %.0f%%)",
              serviceName, value, rule.getThreshold());
      case CONSECUTIVE_FAILURES ->
          String.format("Service '%s' is passing health checks again", serviceName);
      default -> throw new IllegalArgumentException("Not a threshold condition: " + rule.getCondition());
    };
  }
}
