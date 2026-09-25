package com.servicedna.ingestion.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code key} is only present in the response that creates the key. */
public record IngestionKeyDto(
    UUID id,
    String name,
    String keyPrefix,
    String key,
    String createdByEmail,
    OffsetDateTime createdAt,
    OffsetDateTime lastUsedAt,
    OffsetDateTime revokedAt) {}
