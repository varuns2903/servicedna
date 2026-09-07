package com.servicedna.alert.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.alert.config.KafkaTopicConfig;
import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.AlertRule;
import com.servicedna.alert.event.ServiceStatusChangedEvent;
import com.servicedna.alert.repository.AlertRuleRepository;
import com.servicedna.service.domain.ServiceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
public class AlertEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertEventConsumer.class);
    
    private final AlertRuleRepository alertRuleRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public AlertEventConsumer(AlertRuleRepository alertRuleRepository, ObjectMapper objectMapper) {
        this.alertRuleRepository = alertRuleRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    @KafkaListener(topics = KafkaTopicConfig.SERVICE_EVENTS_TOPIC, groupId = "sdna-alerts-group")
    public void consumeStatusChangedEvent(String payload) {
        log.info("Received event: {}", payload);
        try {
            ServiceStatusChangedEvent event = objectMapper.readValue(payload, ServiceStatusChangedEvent.class);
            
            AlertCondition triggeredCondition = determineCondition(event.newStatus());
            if (triggeredCondition != null) {
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
        log.info("Triggering webhook for rule ID: {} to URL: {}", rule.getId(), rule.getWebhookUrl());
        
        try {
            // Simulated payload wrapper
            WebhookPayload webhookPayload = new WebhookPayload(
                    "Service status changed to " + event.newStatus(),
                    event
            );
            
            // We use restTemplate to fire-and-forget the webhook
            restTemplate.postForEntity(rule.getWebhookUrl(), webhookPayload, String.class);
            log.info("Webhook delivered successfully to {}", rule.getWebhookUrl());
        } catch (RestClientException e) {
            log.warn("Webhook delivery failed to {}: {}", rule.getWebhookUrl(), e.getMessage());
            // In a real production system, implement a retry mechanism or dead-letter queue.
        }
    }

    private record WebhookPayload(String message, ServiceStatusChangedEvent event) {}
}
