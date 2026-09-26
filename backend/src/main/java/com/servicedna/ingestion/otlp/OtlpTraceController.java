package com.servicedna.ingestion.otlp;

import com.google.protobuf.InvalidProtocolBufferException;
import com.servicedna.common.exception.ApiException;
import com.servicedna.ingestion.event.TraceBatchReceivedEvent;
import com.servicedna.ingestion.service.IngestionKeyService;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OTLP/HTTP trace receiver. Point any OpenTelemetry SDK or collector at it:
 *
 * <pre>
 * OTEL_EXPORTER_OTLP_ENDPOINT=https://servicedna.example.com/api/v1/otlp
 * OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
 * OTEL_EXPORTER_OTLP_HEADERS=x-servicedna-key=sdna_...
 * </pre>
 *
 * gRPC and Zipkin clients go through the bundled OpenTelemetry Collector, which forwards here.
 */
@RestController
@RequestMapping("/api/v1/otlp/v1")
public class OtlpTraceController {

  private static final Logger log = LoggerFactory.getLogger(OtlpTraceController.class);
  static final String PROTOBUF = "application/x-protobuf";
  private static final int MAX_BATCH_BYTES = 16 * 1024 * 1024;

  private final IngestionKeyService ingestionKeyService;
  private final TraceStoreForwarder traceStore;
  private final ApplicationEventPublisher eventPublisher;
  private final MeterRegistry meterRegistry;

  public OtlpTraceController(
      IngestionKeyService ingestionKeyService,
      TraceStoreForwarder traceStore,
      ApplicationEventPublisher eventPublisher,
      MeterRegistry meterRegistry) {
    this.ingestionKeyService = ingestionKeyService;
    this.traceStore = traceStore;
    this.eventPublisher = eventPublisher;
    this.meterRegistry = meterRegistry;
  }

  @PostMapping(value = "/traces", consumes = PROTOBUF, produces = PROTOBUF)
  public ResponseEntity<byte[]> exportTraces(
      @RequestHeader(value = "x-servicedna-key", required = false) String keyHeader,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "Content-Encoding", required = false) String contentEncoding,
      @RequestBody byte[] rawBody) {
    UUID organizationId =
        ingestionKeyService
            .authenticate(keyHeader != null ? keyHeader : bearer(authorization))
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_INGESTION_KEY",
                        "Send a valid ingestion key in the x-servicedna-key header."));

    byte[] body = decode(rawBody, contentEncoding);
    ExportTraceServiceRequest request;
    try {
      request = ExportTraceServiceRequest.parseFrom(body);
    } catch (InvalidProtocolBufferException e) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_OTLP", "Body is not an OTLP ExportTraceServiceRequest.");
    }

    // Store before notifying listeners: if storage fails the client retries the whole batch, so
    // listeners must not have seen it yet.
    try {
      traceStore.forward(organizationId, body);
    } catch (TraceStoreForwarder.TraceStoreUnavailableException e) {
      log.warn("Trace store unavailable, asking client to retry: {}", e.getMessage());
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "TRACE_STORE_UNAVAILABLE", "Trace storage is unavailable; retry later.");
    }
    eventPublisher.publishEvent(new TraceBatchReceivedEvent(organizationId, request));

    meterRegistry.counter("sdna.ingest.spans").increment(spanCount(request));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(PROTOBUF))
        .body(ExportTraceServiceResponse.getDefaultInstance().toByteArray());
  }

  /** OTLP exporters (the collector's included) gzip by default. */
  private static byte[] decode(byte[] raw, String contentEncoding) {
    if (contentEncoding == null || contentEncoding.isBlank() || contentEncoding.equalsIgnoreCase("identity")) {
      return checkSize(raw);
    }
    if (!contentEncoding.equalsIgnoreCase("gzip")) {
      throw new ApiException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_ENCODING", "Use gzip or no compression.");
    }
    try (var in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(raw))) {
      // Read one byte past the limit so an oversized (or zip-bomb) payload is detected, not kept.
      return checkSize(in.readNBytes(MAX_BATCH_BYTES + 1));
    } catch (java.io.IOException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OTLP", "Body is not valid gzip.");
    }
  }

  private static byte[] checkSize(byte[] body) {
    if (body.length > MAX_BATCH_BYTES) {
      throw new ApiException(
          HttpStatus.PAYLOAD_TOO_LARGE, "BATCH_TOO_LARGE", "OTLP batches are limited to 16 MB.");
    }
    return body;
  }

  private static String bearer(String authorization) {
    return authorization != null && authorization.startsWith("Bearer ")
        ? authorization.substring("Bearer ".length())
        : null;
  }

  private static long spanCount(ExportTraceServiceRequest request) {
    return request.getResourceSpansList().stream()
        .flatMap(rs -> rs.getScopeSpansList().stream())
        .mapToLong(ss -> ss.getSpansCount())
        .sum();
  }
}
