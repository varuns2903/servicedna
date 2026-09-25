package com.servicedna.alert.service;

import com.servicedna.alert.domain.AlertRule;
import com.servicedna.alert.event.ServiceStatusChangedEvent;
import com.servicedna.alert.repository.AlertRuleRepository;
import com.servicedna.incident.service.IncidentService;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.repository.ServiceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * What happens when alert rules fire: webhooks and incidents. Shared by status-change rules
 * ({@link AlertEventConsumer}) and threshold rules ({@link AlertThresholdEvaluator}).
 *
 * <p>Every action is best-effort — a failure is logged, never rethrown — so one broken webhook
 * or incident update can't block the rest, or make Kafka redeliver an event and re-send them all.
 */
@Component
public class AlertActionService {

  private static final Logger log = LoggerFactory.getLogger(AlertActionService.class);

  private final IncidentService incidentService;
  private final ServiceRepository serviceRepository;
  private final AlertRuleRepository alertRuleRepository;
  private final MeterRegistry meterRegistry;
  private final RestTemplate restTemplate = new RestTemplate();

  public AlertActionService(
      IncidentService incidentService,
      ServiceRepository serviceRepository,
      AlertRuleRepository alertRuleRepository,
      MeterRegistry meterRegistry) {
    this.incidentService = incidentService;
    this.serviceRepository = serviceRepository;
    this.alertRuleRepository = alertRuleRepository;
    this.meterRegistry = meterRegistry;
  }

  /** Details attached to generic webhook payloads; {@code event} is null for threshold rules. */
  public record AlertContext(
      String serviceName,
      ServiceStatusChangedEvent event,
      Double value,
      Double threshold) {}

  public void sendWebhooks(Collection<AlertRule> rules, String message, AlertContext context) {
    for (AlertRule rule : rules) {
      if (rule.getWebhookUrl() != null) {
        sendWebhook(rule, message, context);
      }
    }
  }

  /** Opens or updates the service's alert incident at the most severe severity the rules ask for. */
  public void openIncident(
      Collection<AlertRule> rules,
      UUID organizationId,
      UUID serviceId,
      String serviceName,
      String headline,
      String change) {
    rules.stream()
        .map(AlertRule::getIncidentSeverity)
        .filter(Objects::nonNull)
        .min(Comparator.naturalOrder()) // IncidentSeverity is declared most severe first
        .ifPresent(
            severity ->
                run(
                    serviceId,
                    () ->
                        incidentService.openOrUpdateAlertIncident(
                            organizationId, serviceId, serviceName, headline, change, severity)));
  }

  /**
   * Resolves the service's alert incident if nothing it covers is still affected. Does nothing
   * while one of this service's own threshold rules is still breached.
   */
  public void resolveIncident(UUID organizationId, UUID serviceId, String serviceName) {
    if (alertRuleRepository.existsByServiceIdAndBreachedTrue(serviceId)) {
      return;
    }
    run(
        serviceId,
        () ->
            incidentService.resolveAlertIncident(
                organizationId, serviceId, serviceName, this::isStillAffected));
  }

  public String serviceName(UUID serviceId) {
    return serviceRepository
        .findById(serviceId)
        .map(com.servicedna.service.domain.Service::getName)
        .orElse(serviceId.toString());
  }

  private boolean isStillAffected(UUID serviceId) {
    boolean healthy =
        serviceRepository
            .findById(serviceId)
            .map(s -> s.getStatus() == ServiceStatus.HEALTHY)
            .orElse(true);
    return !healthy || alertRuleRepository.existsByServiceIdAndBreachedTrue(serviceId);
  }

  private void sendWebhook(AlertRule rule, String message, AlertContext context) {
    log.info(
        "Triggering {} webhook for rule ID: {} to URL: {}",
        rule.getIntegrationType(),
        rule.getId(),
        rule.getWebhookUrl());
    Object payload =
        switch (rule.getIntegrationType()) {
          case SLACK -> new SlackPayload(message);
          case DISCORD -> new DiscordPayload(message);
          case GENERIC ->
              new WebhookPayload(
                  message,
                  context.serviceName(),
                  rule.getCondition().name(),
                  context.event(),
                  context.value(),
                  context.threshold());
        };
    try {
      restTemplate.postForEntity(rule.getWebhookUrl(), payload, String.class);
      log.info("Webhook delivered successfully to {}", rule.getWebhookUrl());
      meterRegistry
          .counter("sdna.alerts.delivered.count", "integration", rule.getIntegrationType().name())
          .increment();
    } catch (RestClientException e) {
      log.warn("Webhook delivery failed to {}: {}", rule.getWebhookUrl(), e.getMessage());
      meterRegistry
          .counter("sdna.alerts.failed.count", "integration", rule.getIntegrationType().name())
          .increment();
    }
  }

  private void run(UUID serviceId, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      log.error("Incident update for service {} failed", serviceId, e);
    }
  }

  private record WebhookPayload(
      String message,
      String serviceName,
      String condition,
      ServiceStatusChangedEvent event,
      Double value,
      Double threshold) {}

  private record SlackPayload(String text) {}

  private record DiscordPayload(String content) {}
}
