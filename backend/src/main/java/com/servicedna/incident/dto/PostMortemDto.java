package com.servicedna.incident.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PostMortemDto(
        UUID id,
        UUID incidentId,
        String rootCause,
        String timeline,
        String actionItems,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
