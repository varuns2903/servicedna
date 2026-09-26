package com.servicedna.testrun;

import com.servicedna.common.exception.ApiException;
import com.servicedna.graph.domain.Protocol;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.traces.TraceDto;
import com.servicedna.traces.TraceQueryService;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Re-sends a request recorded in a trace — typically a failure captured in production (with
 * SERVICEDNA_CAPTURE_ON_ERROR) — through a runner, usually into a non-production environment, to
 * reproduce it. The request is rebuilt from the span: HTTP method and path, gRPC method, or Kafka
 * topic and key, plus the body the service received. Environment rules and rate limits are those
 * of any test run.
 */
@Service
public class TestRunReplay {

  private final TraceQueryService traces;
  private final TestRunService testRuns;
  private final OrganizationService organizationService;

  public TestRunReplay(TraceQueryService traces, TestRunService testRuns, OrganizationService organizationService) {
    this.traces = traces;
    this.testRuns = testRuns;
    this.organizationService = organizationService;
  }

  public record ReplayRequest(String traceId, String spanId, String environment, Boolean testMode) {}

  public TestRun replay(UUID organizationId, ReplayRequest request, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    if (request.traceId() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TEST_RUN", "Give the trace to replay.");
    }
    TraceDto.Trace trace = traces.trace(organizationId, request.traceId());
    return testRuns.create(organizationId, rebuild(trace, request.spanId(), request.environment(), request.testMode()), userId);
  }

  /** The request a span received, as a test run request. */
  static TestRunDto.CreateRequest rebuild(TraceDto.Trace trace, String spanId, String environment, Boolean testMode) {
    TraceDto.Span span = spanId != null && !spanId.isBlank() ? find(trace, spanId) : entry(trace);
    Map<String, String> a = span.attributes();
    String body = a.get("sdna.request.body");
    if ("grpc".equals(a.get("rpc.system"))) {
      String method = a.get("rpc.service") != null && a.get("rpc.method") != null
          ? a.get("rpc.service") + "/" + a.get("rpc.method")
          : span.name().replaceFirst("^/", "");
      return new TestRunDto.CreateRequest(environment, Protocol.GRPC, null, span.service(), null, null, method, null, null, null,
          body == null ? "{}" : body, testMode);
    }
    if (a.containsKey("messaging.system")) {
      String topic = a.get("messaging.destination.name");
      if (topic == null) {
        throw notReplayable("the span doesn't name its topic");
      }
      if (body == null) {
        throw notReplayable("the message wasn't captured — turn on SERVICEDNA_CAPTURE_ON_ERROR, or replay from a test run");
      }
      return new TestRunDto.CreateRequest(environment, Protocol.MESSAGING, null, null, null, null, null, topic,
          a.get("messaging.kafka.message.key"), null, body, testMode);
    }
    String method = a.getOrDefault("http.request.method", a.get("http.method"));
    String path = a.get("url.path") != null
        ? a.get("url.path") + (a.get("url.query") != null ? "?" + a.get("url.query") : "")
        : a.get("http.target");
    if (method == null || path == null) {
      throw notReplayable("it isn't an HTTP, gRPC or Kafka request");
    }
    if (body == null && !Set.of("GET", "HEAD", "DELETE", "OPTIONS").contains(method.toUpperCase())) {
      throw notReplayable("its body wasn't captured — turn on SERVICEDNA_CAPTURE_ON_ERROR, or replay from a test run");
    }
    Map<String, String> headers = body != null ? Map.of("content-type", "application/json") : null;
    return new TestRunDto.CreateRequest(environment, Protocol.HTTP, null, span.service(), method, path, null, null, null, headers,
        body, testMode);
  }

  /** The first request into an instrumented service: the root server/consumer span. */
  private static TraceDto.Span entry(TraceDto.Trace trace) {
    Map<String, TraceDto.Span> byId = trace.spans().stream().collect(Collectors.toMap(TraceDto.Span::spanId, sp -> sp, (x, y) -> x));
    return trace.spans().stream()
        .filter(TestRunReplay::inbound)
        .filter(s -> {
          // No inbound span above it: nothing instrumented called it.
          for (TraceDto.Span p = byId.get(s.parentSpanId()); p != null; p = byId.get(p.parentSpanId())) {
            if (inbound(p)) {
              return false;
            }
          }
          return true;
        })
        .findFirst()
        .orElseThrow(() -> notReplayable("no service received a request in it"));
  }

  private static boolean inbound(TraceDto.Span s) {
    return "SERVER".equals(s.kind()) || "CONSUMER".equals(s.kind());
  }

  private static TraceDto.Span find(TraceDto.Trace trace, String spanId) {
    return trace.spans().stream().filter(s -> s.spanId().equalsIgnoreCase(spanId)).findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPAN_NOT_FOUND", "No span " + spanId + " in the trace."));
  }

  private static ApiException notReplayable(String why) {
    return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_REPLAYABLE", "This request can't be replayed: " + why + ".");
  }
}
