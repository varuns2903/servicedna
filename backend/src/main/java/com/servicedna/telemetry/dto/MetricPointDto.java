package com.servicedna.telemetry.dto;

import com.servicedna.service.domain.ServiceStatus;
import java.time.OffsetDateTime;

public record MetricPointDto(OffsetDateTime timestamp, ServiceStatus status, Integer latencyMs) {}
