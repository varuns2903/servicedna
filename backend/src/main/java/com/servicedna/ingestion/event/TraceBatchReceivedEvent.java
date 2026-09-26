package com.servicedna.ingestion.event;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import java.util.UUID;

/**
 * An authenticated batch of OTLP spans for an organization, published in-process after it has
 * been stored. Listeners (service self-registration, dependency discovery) must be fast or
 * asynchronous: they run on the ingestion request thread.
 */
public record TraceBatchReceivedEvent(UUID organizationId, ExportTraceServiceRequest request) {}
