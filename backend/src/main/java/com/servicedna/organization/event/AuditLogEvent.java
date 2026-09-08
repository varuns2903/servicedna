package com.servicedna.organization.event;

import java.util.UUID;

public record AuditLogEvent(
        UUID organizationId,
        UUID userId,
        String action,
        String entityType,
        String entityId,
        String details,
        String ipAddress
) {}
