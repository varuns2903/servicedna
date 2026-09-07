package com.servicedna.incident.dto;

import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.incident.domain.IncidentStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record IncidentDto(
    UUID id,
    UUID organizationId,
    UUID createdById,
    String title,
    String description,
    IncidentStatus status,
    IncidentSeverity severity,
    List<UUID> affectedServiceIds,
    OffsetDateTime resolvedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) implements java.io.Serializable {}
