package com.servicedna.alert.dto;

import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.IntegrationType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AlertRuleDto(
    UUID id,
    UUID organizationId,
    UUID serviceId,
    AlertCondition condition,
    String webhookUrl,
    IntegrationType integrationType,
    IncidentSeverity incidentSeverity,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
