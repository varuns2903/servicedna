package com.servicedna.telemetry.service;

import com.servicedna.alert.event.ServiceStatusChangedEvent;
import com.servicedna.alert.service.AlertEventPublisher;
import com.servicedna.common.exception.ApiException;
import com.servicedna.service.domain.Service;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.telemetry.domain.ServicePing;
import com.servicedna.telemetry.dto.PingRequest;
import com.servicedna.telemetry.repository.ServicePingRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@org.springframework.stereotype.Service
public class PingService {

    private final ServiceRepository serviceRepository;
    private final ServicePingRepository servicePingRepository;
    private final AlertEventPublisher alertEventPublisher;
    private final StringRedisTemplate redisTemplate;

    public PingService(
            ServiceRepository serviceRepository,
            ServicePingRepository servicePingRepository,
            @Lazy AlertEventPublisher alertEventPublisher,
            StringRedisTemplate redisTemplate
    ) {
        this.serviceRepository = serviceRepository;
        this.servicePingRepository = servicePingRepository;
        this.alertEventPublisher = alertEventPublisher;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public void processPing(String apiKey, PingRequest request) {
        String cacheKey = "apikey:" + apiKey;
        String serviceIdStr = redisTemplate.opsForValue().get(cacheKey);
        
        Service service;
        if (serviceIdStr != null) {
            service = serviceRepository.findById(UUID.fromString(serviceIdStr))
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_API_KEY", "Invalid API Key"));
        } else {
            service = serviceRepository.findByApiKey(apiKey)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_API_KEY", "Invalid API Key"));
            redisTemplate.opsForValue().set(cacheKey, service.getId().toString(), Duration.ofHours(1));
        }

        ServicePing ping = new ServicePing(
                UUID.randomUUID(),
                service,
                request.status(),
                request.latencyMs(),
                request.message()
        );
        servicePingRepository.save(ping);

        ServiceStatus oldStatus = service.getStatus();
        ServiceStatus newStatus = request.status();

        if (oldStatus != newStatus) {
            service.setStatus(newStatus);
            serviceRepository.save(service);

            if (alertEventPublisher != null) {
                alertEventPublisher.publishStatusChangedEvent(new ServiceStatusChangedEvent(
                        service.getId(),
                        service.getOrganization().getId(),
                        oldStatus,
                        newStatus,
                        OffsetDateTime.now()
                ));
            }
        }
    }
}
