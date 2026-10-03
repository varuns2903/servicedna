package com.servicedna.alert.service;

import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.AlertRule;
import com.servicedna.alert.repository.AlertRuleRepository;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.repository.MaintenanceWindowRepository;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.telemetry.domain.ServicePing;
import com.servicedna.telemetry.repository.ServicePingRepository;
import com.servicedna.telemetry.requests.RequestStats;
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
 * Evaluates threshold alert rules (LATENCY_ABOVE, ERROR_RATE_ABOVE, CONSECUTIVE_FAILURES). Latency
 * and error rate are judged on real traffic when the service sends traces — p95 request latency
 * and the share of failed requests, once it handled enough requests in the window — and otherwise
 * on recent health-check pings from the active prober and push agents. CONSECUTIVE_FAILURES is
 * always about health checks. Each rule fires once when its threshold is crossed and once when it
 * clears, tracked by {@link AlertRule#isBreached()}.
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
  private final RequestStats requestStats;
  private final int minSamples;
  private final int minRequests;

  /** A rule's metric, and whether it came from traces (requests) or health-check pings. */
  record Measurement(double value, boolean fromRequests, long requests) {}

  public AlertThresholdEvaluator(
      AlertRuleRepository alertRuleRepository,
      ServicePingRepository servicePingRepository,
      ServiceRepository serviceRepository,
      MaintenanceWindowRepository maintenanceWindowRepository,
      AlertActionService actions,
      RequestStats requestStats,
      @Value("${alert.threshold-min-samples:3}") int minSamples,
      @Value("${alert.threshold-min-requests:20}") int minRequests) {
    this.alertRuleRepository = alertRuleRepository;
    this.servicePingRepository = servicePingRepository;
    this.serviceRepository = serviceRepository;
    this.maintenanceWindowRepository = maintenanceWindowRepository;
    this.actions = actions;
    this.requestStats = requestStats;
    this.minSamples = Math.max(1, minSamples);
    this.minRequests = Math.max(1, minRequests);
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
    Measurement measured = measure(rule, serviceId, now);
    if (measured == null) {
      return; // not enough data to judge either way; keep the current state
    }
    double value = measured.value();

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
      String message = breachMessage(rule, serviceName, measured);
      actions.sendWebhooks(rules, message, context);
      actions.openIncident(rules, organizationId, serviceId, serviceName, headline(rule), message);
    } else {
      actions.sendWebhooks(rules, clearedMessage(rule, serviceName, measured), context);
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

  /** The rule's metric — from traffic when there's enough, else from pings — or null when there's too little to judge. */
  private Measurement measure(AlertRule rule, UUID serviceId, OffsetDateTime now) {
    if (rule.getCondition() != AlertCondition.CONSECUTIVE_FAILURES) {
      RequestStats.Window traffic = requestStats.window(serviceId, now.minusMinutes(rule.getWindowMinutes()));
      if (traffic.requests() >= minRequests) {
        double value = rule.getCondition() == AlertCondition.ERROR_RATE_ABOVE ? traffic.errorRatePercent() : traffic.percentileMs(95);
        return new Measurement(value, true, traffic.requests());
      }
    }
    Double fromPings = measurePings(rule, serviceId, now);
    return fromPings == null ? null : new Measurement(fromPings, false, 0);
  }

  /** The rule's metric from health-check pings, or null when there are too few to judge. */
  private Double measurePings(AlertRule rule, UUID serviceId, OffsetDateTime now) {
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

  private static String breachMessage(AlertRule rule, String serviceName, Measurement m) {
    double value = m.value();
    return switch (rule.getCondition()) {
      case LATENCY_ABOVE ->
          String.format(
              "Service '%s' %s %.0f ms over the last %d min is above %.0f ms",
              serviceName, latencyName(m), value, rule.getWindowMinutes(), rule.getThreshold());
      case ERROR_RATE_ABOVE ->
          String.format(
              "Service '%s' %s %.0f%% over the last %d min is above %.0f%%",
              serviceName, errorRateName(m), value, rule.getWindowMinutes(), rule.getThreshold());
      case CONSECUTIVE_FAILURES ->
          String.format(
              "Service '%s' failed its last %.0f health checks", serviceName, rule.getThreshold());
      default -> throw new IllegalArgumentException("Not a threshold condition: " + rule.getCondition());
    };
  }

  private static String clearedMessage(AlertRule rule, String serviceName, Measurement m) {
    double value = m.value();
    return switch (rule.getCondition()) {
      case LATENCY_ABOVE ->
          String.format(
              "Service '%s' %s is back to %.0f ms (threshold %.0f ms)",
              serviceName, latencyName(m), value, rule.getThreshold());
      case ERROR_RATE_ABOVE ->
          String.format(
              "Service '%s' %s is back to %.0f%% (threshold %.0f%%)",
              serviceName, errorRateName(m), value, rule.getThreshold());
      case CONSECUTIVE_FAILURES ->
          String.format("Service '%s' is passing health checks again", serviceName);
      default -> throw new IllegalArgumentException("Not a threshold condition: " + rule.getCondition());
    };
  }

  private static String latencyName(Measurement m) {
    return m.fromRequests() ? "p95 request latency (" + m.requests() + " requests)" : "average health-check latency";
  }

  private static String errorRateName(Measurement m) {
    return m.fromRequests() ? "request error rate (" + m.requests() + " requests)" : "health-check failure rate";
  }
}
