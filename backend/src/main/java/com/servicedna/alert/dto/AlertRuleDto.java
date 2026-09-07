package com.servicedna.alert.dto;

import com.servicedna.alert.domain.AlertCondition;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AlertRuleDto(
    UUID id,
    UUID organizationId,
    UUID serviceId,
    AlertCondition condition,
    String webhookUrl,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
