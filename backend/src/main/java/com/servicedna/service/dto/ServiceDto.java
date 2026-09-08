package com.servicedna.service.dto;

import com.servicedna.service.domain.ServiceStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ServiceDto(
    UUID id,
    UUID organizationId,
    String name,
    String description,
    String repositoryUrl,
    String region,
    ServiceStatus status,
    String apiKey,
    List<UUID> dependencyIds,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
