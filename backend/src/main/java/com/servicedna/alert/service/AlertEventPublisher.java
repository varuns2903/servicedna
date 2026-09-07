package com.servicedna.alert.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.servicedna.alert.config.KafkaTopicConfig;
import com.servicedna.alert.event.ServiceStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AlertEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlertEventPublisher.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AlertEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        // ensure JavaTimeModule is registered (usually handled by Spring Boot's autoconfigured ObjectMapper, but explicitly ensuring it here is safe)
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void publishStatusChangedEvent(ServiceStatusChangedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            log.info("Publishing event to {}: {}", KafkaTopicConfig.SERVICE_EVENTS_TOPIC, payload);
            kafkaTemplate.send(KafkaTopicConfig.SERVICE_EVENTS_TOPIC, event.serviceId().toString(), payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event", e);
        }
    }
}
