package com.servicedna.service.dto;

/**
 * What a service's telemetry says about it (OpenTelemetry resource attributes). Only {@code name}
 * is required; everything else may be null.
 */
public record TelemetryIdentity(
    String name,
    String environment,
    String language,
    String version,
    String region,
    String healthCheckUrl) {}
