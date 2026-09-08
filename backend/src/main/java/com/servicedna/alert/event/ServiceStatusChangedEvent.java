package com.servicedna.alert.event;

import com.servicedna.service.domain.ServiceStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceStatusChangedEvent(
    UUID serviceId,
    UUID organizationId,
    ServiceStatus oldStatus,
    ServiceStatus newStatus,
    OffsetDateTime timestamp) {}
