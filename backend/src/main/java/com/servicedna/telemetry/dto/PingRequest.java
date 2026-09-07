package com.servicedna.telemetry.dto;

import com.servicedna.service.domain.ServiceStatus;
import jakarta.validation.constraints.NotNull;

public record PingRequest(
    @NotNull(message = "Status is required")
    ServiceStatus status,
    
    Integer latencyMs,
    
    String message
) {}
