package com.servicedna.alert.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String SERVICE_EVENTS_TOPIC = "sdna.service.events";

    @Bean
    public NewTopic serviceEventsTopic() {
        return TopicBuilder.name(SERVICE_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
