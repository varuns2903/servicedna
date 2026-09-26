package com.servicedna.ingestion.otlp;

import com.google.protobuf.InvalidProtocolBufferException;
import com.servicedna.common.exception.ApiException;
import com.servicedna.ingestion.service.IngestionKeyService;
import com.servicedna.logs.LogStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OTLP/HTTP log receiver, next to the trace receiver: same endpoint base, same ingestion keys.
 * Logs are stored in the log store with the organization as tenant; records carrying a trace id
 * show up on that trace.
 */
@RestController
@RequestMapping("/api/v1/otlp/v1")
public class OtlpLogController {

  private static final Logger log = LoggerFactory.getLogger(OtlpLogController.class);

  private final IngestionKeyService ingestionKeyService;
  private final LogStore logStore;
  private final MeterRegistry meterRegistry;

  public OtlpLogController(IngestionKeyService ingestionKeyService, LogStore logStore, MeterRegistry meterRegistry) {
    this.ingestionKeyService = ingestionKeyService;
    this.logStore = logStore;
    this.meterRegistry = meterRegistry;
  }

  @PostMapping(value = "/logs", consumes = OtlpTraceController.PROTOBUF, produces = OtlpTraceController.PROTOBUF)
  public ResponseEntity<byte[]> exportLogs(
      @RequestHeader(value = "x-servicedna-key", required = false) String keyHeader,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "Content-Encoding", required = false) String contentEncoding,
      @RequestBody byte[] rawBody) {
    UUID organizationId =
        ingestionKeyService
            .authenticate(keyHeader != null ? keyHeader : OtlpTraceController.bearer(authorization))
            .orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED, "INVALID_INGESTION_KEY", "Send a valid ingestion key in the x-servicedna-key header."));

    byte[] body = OtlpTraceController.decode(rawBody, contentEncoding);
    ExportLogsServiceRequest request;
    try {
      request = ExportLogsServiceRequest.parseFrom(body);
    } catch (InvalidProtocolBufferException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OTLP", "Body is not an OTLP ExportLogsServiceRequest.");
    }
    try {
      logStore.forward(organizationId, body);
    } catch (LogStore.LogStoreUnavailableException e) {
      log.warn("Log store unavailable, asking client to retry: {}", e.getMessage());
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "LOG_STORE_UNAVAILABLE", "Log storage is unavailable; retry later.");
    }
    meterRegistry.counter("sdna.ingest.logs").increment(
        request.getResourceLogsList().stream().flatMap(r -> r.getScopeLogsList().stream()).mapToLong(s -> s.getLogRecordsCount()).sum());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(OtlpTraceController.PROTOBUF))
        .body(ExportLogsServiceResponse.getDefaultInstance().toByteArray());
  }
}
