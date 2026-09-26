package com.servicedna.telemetry.dto;

import com.servicedna.service.domain.ServiceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code service} and {@code environment} identify the service when pinging with an organization
 * ingestion key; with a per-service API key they're ignored.
 */
public record PingRequest(
    @NotNull(message = "Status is required") ServiceStatus status,
    Integer latencyMs,
    String message,
    @Size(max = 255) String service,
    @Size(max = 64) String environment) {}
