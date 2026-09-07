package com.servicedna.common.config;

import com.servicedna.incident.repository.IncidentRepository;
import com.servicedna.service.repository.ServiceRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    private final MeterRegistry meterRegistry;
    private final ServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;

    public MetricsConfig(MeterRegistry meterRegistry, ServiceRepository serviceRepository, IncidentRepository incidentRepository) {
        this.meterRegistry = meterRegistry;
        this.serviceRepository = serviceRepository;
        this.incidentRepository = incidentRepository;
    }

    @PostConstruct
    public void registerCustomMetrics() {
        Gauge.builder("sdna.services.total", serviceRepository, ServiceRepository::count)
                .description("Total number of services registered")
                .register(meterRegistry);

        Gauge.builder("sdna.incidents.total", incidentRepository, IncidentRepository::count)
                .description("Total number of incidents created")
                .register(meterRegistry);
    }
}
