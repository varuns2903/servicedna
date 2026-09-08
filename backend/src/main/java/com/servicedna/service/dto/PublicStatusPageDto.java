package com.servicedna.service.dto;

import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.incident.domain.IncidentStatus;
import com.servicedna.service.domain.ServiceStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PublicStatusPageDto(
        UUID organizationId,
        String organizationName,
        String overallState,
        List<PublicServiceDto> services,
        List<PublicIncidentDto> activeIncidents
) {
    public record PublicServiceDto(
            UUID id,
            String name,
            String description,
            ServiceStatus status
    ) {}

    public record PublicIncidentDto(
            UUID id,
            String title,
            String description,
            IncidentStatus status,
            IncidentSeverity severity,
            OffsetDateTime createdAt,
            OffsetDateTime resolvedAt
    ) {}
}
