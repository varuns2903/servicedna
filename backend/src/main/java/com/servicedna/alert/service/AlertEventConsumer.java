package com.servicedna.alert.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.alert.config.KafkaTopicConfig;
import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.AlertRule;
import com.servicedna.alert.event.ServiceStatusChangedEvent;
import com.servicedna.alert.repository.AlertRuleRepository;
import com.servicedna.service.repository.MaintenanceWindowRepository;
import java.time.OffsetDateTime;
import com.servicedna.service.domain.ServiceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;
import com.servicedna.dashboard.event.DashboardInvalidationEvent;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class AlertEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertEventConsumer.class);
    
    private final AlertRuleRepository alertRuleRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final MaintenanceWindowRepository maintenanceWindowRepository;

    public AlertEventConsumer(AlertRuleRepository alertRuleRepository, ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher, MeterRegistry meterRegistry, MaintenanceWindowRepository maintenanceWindowRepository) {
        this.alertRuleRepository = alertRuleRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        this.maintenanceWindowRepository = maintenanceWindowRepository;
    }

    @KafkaListener(topics = KafkaTopicConfig.SERVICE_EVENTS_TOPIC, groupId = "sdna-alerts-group")
    public void consumeStatusChangedEvent(String payload) {
        log.info("Received event: {}", payload);
        try {
            ServiceStatusChangedEvent event = objectMapper.readValue(payload, ServiceStatusChangedEvent.class);
            
            eventPublisher.publishEvent(new DashboardInvalidationEvent(this, event.organizationId()));

            AlertCondition triggeredCondition = determineCondition(event.newStatus());
            if (triggeredCondition != null) {
                // Check if the service is in active maintenance
                boolean inMaintenance = maintenanceWindowRepository.isServiceInActiveMaintenance(event.serviceId(), OffsetDateTime.now());
                if (inMaintenance) {
                    log.info("Alert suppressed for service {} due to active maintenance window", event.serviceId());
                    return;
                }

                List<AlertRule> rules = alertRuleRepository.findByServiceIdAndCondition(event.serviceId(), triggeredCondition);
                for (AlertRule rule : rules) {
                    triggerWebhook(rule, event);
                }
            }

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

    private void triggerWebhook(AlertRule rule, ServiceStatusChangedEvent event) {
        log.info("Triggering {} webhook for rule ID: {} to URL: {}", rule.getIntegrationType(), rule.getId(), rule.getWebhookUrl());
        
        try {
            Object payload;
            String message = String.format("Service '%s' (ID: %s) changed status to %s", 
                event.serviceId(), event.serviceId(), event.newStatus());

            switch (rule.getIntegrationType()) {
                case SLACK:
                    payload = new SlackPayload(message);
                    break;
                case DISCORD:
                    payload = new DiscordPayload(message);
                    break;
                case GENERIC:
                default:
                    payload = new WebhookPayload(message, event);
                    break;
            }
            
            // We use restTemplate to fire-and-forget the webhook
            restTemplate.postForEntity(rule.getWebhookUrl(), payload, String.class);
            log.info("Webhook delivered successfully to {}", rule.getWebhookUrl());
            meterRegistry.counter("sdna.alerts.delivered.count", "integration", rule.getIntegrationType().name()).increment();
        } catch (RestClientException e) {
            log.warn("Webhook delivery failed to {}: {}", rule.getWebhookUrl(), e.getMessage());
            meterRegistry.counter("sdna.alerts.failed.count", "integration", rule.getIntegrationType().name()).increment();
        }
    }

    private record WebhookPayload(String message, ServiceStatusChangedEvent event) {}
    private record SlackPayload(String text) {}
    private record DiscordPayload(String content) {}
}
