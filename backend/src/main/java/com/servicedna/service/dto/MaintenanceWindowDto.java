package com.servicedna.service.dto;

import com.servicedna.service.domain.MaintenanceStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MaintenanceWindowDto(
    UUID id,
    UUID organizationId,
    UUID serviceId,
    String title,
    String description,
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    MaintenanceStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
