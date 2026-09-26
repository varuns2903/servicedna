package com.servicedna.graph.domain;

import java.util.UUID;

/**
 * Identifies an edge at operation granularity. For non-service targets {@code targetServiceId} is
 * null and {@code targetName} names the database, host or topic.
 */
public record CallKey(
    UUID sourceServiceId,
    String sourceOperation,
    UUID targetServiceId,
    TargetKind targetKind,
    String targetName,
    String targetOperation,
    Protocol protocol) {}
