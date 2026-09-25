package com.servicedna.alert.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.alert.config.KafkaTopicConfig;
import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.AlertRule;
import com.servicedna.alert.event.ServiceStatusChangedEvent;
import com.servicedna.alert.repository.AlertRuleRepository;
import com.servicedna.dashboard.event.DashboardInvalidationEvent;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.repository.MaintenanceWindowRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Fires status-change alert rules (STATUS_DOWN / STATUS_DEGRADED / STATUS_RECOVERED). */
@Component
public class AlertEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(AlertEventConsumer.class);

  private final AlertRuleRepository alertRuleRepository;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;
  private final MaintenanceWindowRepository maintenanceWindowRepository;
  private final AlertActionService actions;

  public AlertEventConsumer(
      AlertRuleRepository alertRuleRepository,
      ObjectMapper objectMapper,
      ApplicationEventPublisher eventPublisher,
      MaintenanceWindowRepository maintenanceWindowRepository,
      AlertActionService actions) {
    this.alertRuleRepository = alertRuleRepository;
    this.objectMapper = objectMapper;
    this.eventPublisher = eventPublisher;
    this.maintenanceWindowRepository = maintenanceWindowRepository;
    this.actions = actions;
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
        actions.resolveIncident(
            event.organizationId(), event.serviceId(), actions.serviceName(event.serviceId()));
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

      String serviceName = actions.serviceName(event.serviceId());
      String change =
          serviceName + " changed status from " + event.oldStatus() + " to " + event.newStatus();
      actions.sendWebhooks(
          rules,
          String.format(
              "Service '%s' changed status from %s to %s",
              serviceName, event.oldStatus(), event.newStatus()),
          new AlertActionService.AlertContext(serviceName, event, null, null));
      actions.openIncident(
          rules,
          event.organizationId(),
          event.serviceId(),
          serviceName,
          "is " + event.newStatus(),
          change);
    } catch (JsonProcessingException e) {
      log.error("Failed to deserialize event payload", e);
    }
  }

  private AlertCondition determineCondition(ServiceStatus newStatus) {
    return switch (newStatus) {
      case DOWN -> AlertCondition.STATUS_DOWN;
      case DEGRADED -> AlertCondition.STATUS_DEGRADED;
      case HEALTHY -> AlertCondition.STATUS_RECOVERED;
      case UNKNOWN -> null;
    };
  }
}
