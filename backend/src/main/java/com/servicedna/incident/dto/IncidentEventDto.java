package com.servicedna.incident.dto;

import com.servicedna.incident.domain.IncidentEventType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record IncidentEventDto(
    UUID id,
    IncidentEventType eventType,
    String message,
    String actorEmail,
    OffsetDateTime createdAt) {}
