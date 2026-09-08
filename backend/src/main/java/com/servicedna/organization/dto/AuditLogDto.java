package com.servicedna.organization.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogDto(
    UUID id,
    UUID organizationId,
    UUID userId,
    String userEmail,
    String action,
    String entityType,
    String entityId,
    String details,
    String ipAddress,
    OffsetDateTime createdAt) {}
