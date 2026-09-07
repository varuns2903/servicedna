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
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@org.springframework.stereotype.Service
public class PingService {

    private final ServiceRepository serviceRepository;
    private final ServicePingRepository servicePingRepository;
    private final AlertEventPublisher alertEventPublisher;

    public PingService(
            ServiceRepository serviceRepository,
            ServicePingRepository servicePingRepository,
            @Lazy AlertEventPublisher alertEventPublisher
    ) {
        this.serviceRepository = serviceRepository;
        this.servicePingRepository = servicePingRepository;
        this.alertEventPublisher = alertEventPublisher;
    }

    @Transactional
    public void processPing(String apiKey, PingRequest request) {
        Service service = serviceRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_API_KEY", "Invalid API Key"));

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
