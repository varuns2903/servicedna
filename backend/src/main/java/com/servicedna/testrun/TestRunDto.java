package com.servicedna.testrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.servicedna.graph.domain.Protocol;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TestRunDto {

  private TestRunDto() {}

  /**
   * A request to send through a runner. HTTP/GraphQL: {@code serviceId} + {@code method} + {@code path};
   * gRPC: {@code serviceId} + {@code grpcMethod} ("package.Service/Method"); messaging: {@code topic}
   * (+ optional {@code key}). {@code body} is sent as is (JSON for HTTP and gRPC).
   */
  public record CreateRequest(
      @Size(max = 64) String environment,
      @NotNull Protocol protocol,
      UUID serviceId,
      @Size(max = 16) String method,
      @Size(max = 2048) String path,
      @Size(max = 255) String grpcMethod,
      @Size(max = 255) String topic,
      @Size(max = 255) String key,
      Map<@Size(max = 128) String, @Size(max = 4096) String> headers,
      @Size(max = 262144) String body) {}

  /** A run as the UI and CLI see it; {@code hops} is filled in once the trace has arrived. */
  public record Run(
      UUID id,
      String environment,
      Protocol protocol,
      UUID serviceId,
      String serviceName,
      JsonNode target,
      JsonNode request,
      String traceId,
      TestRunStatus status,
      JsonNode result,
      String error,
      String runner,
      OffsetDateTime createdAt,
      OffsetDateTime finishedAt,
      List<Hop> hops) {}

  /** One service's part in the run: what it received and returned, from its captured spans. */
  public record Hop(
      String spanId,
      String parentSpanId,
      String service,
      String operation,
      String kind,
      OffsetDateTime start,
      double durationMs,
      boolean error,
      String statusMessage,
      Integer httpStatus,
      String requestBody,
      String responseBody,
      Map<String, String> captured) {}

  /** What a runner gets when it claims a run. */
  public record Job(UUID id, UUID organizationId, Protocol protocol, JsonNode target, JsonNode request, String traceId) {}

  /** What a runner reports back after sending the request. */
  public record JobResult(
      boolean sent,
      String error,
      Integer status,
      Map<String, String> headers,
      String body,
      Long durationMs,
      Integer partition,
      Long offset) {}
}
