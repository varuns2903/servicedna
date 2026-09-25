package com.servicedna.alert.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.alert.config.KafkaTopicConfig;
import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.AlertRule;
import com.servicedna.alert.event.ServiceStatusChangedEvent;
import com.servicedna.alert.repository.AlertRuleRepository;
import com.servicedna.dashboard.event.DashboardInvalidationEvent;
import com.servicedna.incident.service.IncidentService;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.repository.MaintenanceWindowRepository;
import com.servicedna.service.repository.ServiceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AlertEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(AlertEventConsumer.class);

  private final AlertRuleRepository alertRuleRepository;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;
  private final ApplicationEventPublisher eventPublisher;
  private final MeterRegistry meterRegistry;
  private final MaintenanceWindowRepository maintenanceWindowRepository;
  private final ServiceRepository serviceRepository;
  private final IncidentService incidentService;

  public AlertEventConsumer(
      AlertRuleRepository alertRuleRepository,
      ObjectMapper objectMapper,
      ApplicationEventPublisher eventPublisher,
      MeterRegistry meterRegistry,
      MaintenanceWindowRepository maintenanceWindowRepository,
      ServiceRepository serviceRepository,
      IncidentService incidentService) {
    this.alertRuleRepository = alertRuleRepository;
    this.objectMapper = objectMapper;
    this.restTemplate = new RestTemplate();
    this.eventPublisher = eventPublisher;
    this.meterRegistry = meterRegistry;
    this.maintenanceWindowRepository = maintenanceWindowRepository;
    this.serviceRepository = serviceRepository;
    this.incidentService = incidentService;
  }

  @KafkaListener(topics = KafkaTopicConfig.SERVICE_EVENTS_TOPIC, groupId = "sdna-alerts-group")
  public void consumeStatusChangedEvent(String payload) {
    log.info("Received event: {}", payload);
    try {
      ServiceStatusChangedEvent event =
          objectMapper.readValue(payload, ServiceStatusChangedEvent.class);

      eventPublisher.publishEvent(new DashboardInvalidationEvent(this, event.organizationId()));

      AlertCondition triggeredCondition = determineCondition(event.newStatus());
      if (triggeredCondition == null) {
        return;
      }

      // Recovery resolves an alert-opened incident whatever the rules say and even during
      // maintenance: the service is healthy, so the incident is over.
      if (triggeredCondition == AlertCondition.STATUS_RECOVERED) {
        runIncidentAction(
            event,
            () ->
                incidentService.resolveAlertIncident(
                    event.organizationId(), event.serviceId(), serviceName(event)));
      }

      boolean inMaintenance =
          maintenanceWindowRepository.isServiceInActiveMaintenance(
              event.serviceId(), OffsetDateTime.now());
      if (inMaintenance) {
        log.info(
            "Alert suppressed for service {} due to active maintenance window", event.serviceId());
        return;
      }

      List<AlertRule> rules =
          alertRuleRepository.findByServiceIdAndCondition(event.serviceId(), triggeredCondition);
      if (rules.isEmpty()) {
        return;
      }

      String serviceName = serviceName(event);
      for (AlertRule rule : rules) {
        if (rule.getWebhookUrl() != null) {
          triggerWebhook(rule, event, serviceName);
        }
      }

      // Several rules may ask for an incident; the most severe one wins (declared first).
      rules.stream()
          .map(AlertRule::getIncidentSeverity)
          .filter(Objects::nonNull)
          .min(Comparator.naturalOrder())
          .ifPresent(
              severity ->
                  runIncidentAction(
                      event,
                      () ->
                          incidentService.openOrUpdateAlertIncident(
                              event.organizationId(),
                              event.serviceId(),
                              serviceName,
                              event.oldStatus(),
                              event.newStatus(),
                              severity)));
    } catch (JsonProcessingException e) {
      log.error("Failed to deserialize event payload", e);
    }
  }

  /**
   * A failure here is logged rather than rethrown: rethrowing would make Kafka redeliver the event
   * and re-send every webhook above.
   */
  private void runIncidentAction(ServiceStatusChangedEvent event, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      log.error("Incident update for service {} failed", event.serviceId(), e);
    }
  }

  // Looked up by id rather than via rule.getService(): that association is lazy, and this
  // listener runs outside a transaction.
  private String serviceName(ServiceStatusChangedEvent event) {
    return serviceRepository
        .findById(event.serviceId())
        .map(com.servicedna.service.domain.Service::getName)
        .orElse(event.serviceId().toString());
  }

  private AlertCondition determineCondition(ServiceStatus newStatus) {
    return switch (newStatus) {
      case DOWN -> AlertCondition.STATUS_DOWN;
      case DEGRADED -> AlertCondition.STATUS_DEGRADED;
      case HEALTHY -> AlertCondition.STATUS_RECOVERED;
      case UNKNOWN -> null;
    };
  }

  private void triggerWebhook(AlertRule rule, ServiceStatusChangedEvent event, String serviceName) {
    log.info(
        "Triggering {} webhook for rule ID: {} to URL: {}",
        rule.getIntegrationType(),
        rule.getId(),
        rule.getWebhookUrl());

    try {
      Object payload;
      String message =
          String.format(
              "Service '%s' changed status from %s to %s",
              serviceName, event.oldStatus(), event.newStatus());

      switch (rule.getIntegrationType()) {
        case SLACK:
          payload = new SlackPayload(message);
          break;
        case DISCORD:
          payload = new DiscordPayload(message);
          break;
        case GENERIC:
        default:
          payload = new WebhookPayload(message, serviceName, event);
          break;
      }

      // We use restTemplate to fire-and-forget the webhook
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

  private record WebhookPayload(
      String message, String serviceName, ServiceStatusChangedEvent event) {}

  private record SlackPayload(String text) {}

  private record DiscordPayload(String content) {}
}
