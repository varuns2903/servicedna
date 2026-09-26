package com.servicedna.traces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.service.OrganizationService;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Reads traces back from the trace store (Grafana Tempo), always as the caller's organization's
 * tenant, so one organization can never see another's traces.
 */
@Service
public class TraceQueryService {

  private static final Pattern TRACE_ID = Pattern.compile("^[0-9a-fA-F]{16,32}$");

  private final String baseUrl;
  private final OrganizationService organizationService;
  private final ObjectMapper json;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public TraceQueryService(
      @Value("${trace-store.query-url:}") String baseUrl, OrganizationService organizationService, ObjectMapper json) {
    this.baseUrl = baseUrl == null || baseUrl.isBlank() ? null : baseUrl.replaceAll("/+$", "");
    this.organizationService = organizationService;
    this.json = json;
  }

  /** Where one operation called another: {@code {caller} >> {callee}}. */
  public record Hop(String service, String operation) {}

  public List<TraceDto.Summary> search(
      UUID organizationId, Hop caller, Hop callee, boolean errorsOnly, int windowMinutes, int limit, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    List<String> selectors = new ArrayList<>();
    if (caller != null) {
      selectors.add(selector(caller, false));
    }
    if (callee != null) {
      selectors.add(selector(callee, errorsOnly));
    } else if (errorsOnly && !selectors.isEmpty()) {
      selectors.set(selectors.size() - 1, selector(caller, true));
    }
    if (selectors.isEmpty()) {
      selectors.add(errorsOnly ? "{ status = error }" : "{ }");
    }
    String query = String.join(" >> ", selectors);
    long end = Instant.now().getEpochSecond();
    long start = end - Math.max(1, Math.min(windowMinutes, 7 * 24 * 60)) * 60L;
    JsonNode body =
        get(organizationId, "/api/search?q=" + encode(query) + "&limit=" + Math.max(1, Math.min(limit, 100)) + "&start=" + start + "&end=" + end);

    List<TraceDto.Summary> result = new ArrayList<>();
    for (JsonNode t : body.path("traces")) {
      result.add(
          new TraceDto.Summary(
              t.path("traceID").asText(),
              t.path("rootServiceName").asText(null),
              t.path("rootTraceName").asText(null),
              Instant.ofEpochSecond(0, t.path("startTimeUnixNano").asLong()),
              t.path("durationMs").asLong(),
              errorsOnly));
    }
    result.sort(Comparator.comparing(TraceDto.Summary::start).reversed());
    return result;
  }

  public TraceDto.Trace trace(UUID organizationId, String traceId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    return trace(organizationId, traceId);
  }

  /** For internal callers that have already established the organization (e.g. test runs). */
  public TraceDto.Trace trace(UUID organizationId, String traceId) {
    if (!TRACE_ID.matcher(traceId).matches()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRACE_ID", "Trace ids are 16 or 32 hex characters.");
    }
    JsonNode body = get(organizationId, "/api/traces/" + traceId);
    List<TraceDto.Span> spans = new ArrayList<>();
    for (JsonNode batch : body.path("batches")) {
      String service = attributes(batch.path("resource").path("attributes")).getOrDefault("service.name", "unknown");
      for (JsonNode scope : batch.path("scopeSpans")) {
        for (JsonNode s : scope.path("spans")) {
          long startNs = s.path("startTimeUnixNano").asLong();
          long endNs = s.path("endTimeUnixNano").asLong();
          List<TraceDto.Event> events = new ArrayList<>();
          for (JsonNode e : s.path("events")) {
            events.add(new TraceDto.Event(e.path("name").asText(), Instant.ofEpochSecond(0, e.path("timeUnixNano").asLong()), attributes(e.path("attributes"))));
          }
          JsonNode status = s.path("status");
          spans.add(
              new TraceDto.Span(
                  hex(s.path("spanId").asText()),
                  s.hasNonNull("parentSpanId") ? hex(s.path("parentSpanId").asText()) : null,
                  service,
                  s.path("name").asText(),
                  s.path("kind").asText("SPAN_KIND_INTERNAL").replace("SPAN_KIND_", ""),
                  Instant.ofEpochSecond(0, startNs),
                  Math.round((endNs - startNs) / 10_000.0) / 100.0,
                  "STATUS_CODE_ERROR".equals(status.path("code").asText()),
                  status.path("message").asText(null),
                  attributes(s.path("attributes")),
                  events));
        }
      }
    }
    if (spans.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "TRACE_NOT_FOUND", "Trace not found.");
    }
    spans.sort(Comparator.comparing(TraceDto.Span::start));
    Instant start = spans.get(0).start();
    Instant end = spans.stream().map(s -> s.start().plusNanos((long) (s.durationMs() * 1_000_000))).max(Comparator.naturalOrder()).orElse(start);
    return new TraceDto.Trace(traceId.toLowerCase(), start, Duration.between(start, end).toMillis(), spans);
  }

  private JsonNode get(UUID organizationId, String path) {
    if (baseUrl == null) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TRACE_STORE_DISABLED", "Trace storage isn't configured (TRACE_STORE_QUERY_URL).");
    }
    try {
      HttpResponse<String> res =
          client.send(
              HttpRequest.newBuilder(URI.create(baseUrl + path))
                  .timeout(Duration.ofSeconds(15))
                  .header("X-Scope-OrgID", organizationId.toString())
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() == 404) {
        throw new ApiException(HttpStatus.NOT_FOUND, "TRACE_NOT_FOUND", "Trace not found.");
      }
      if (res.statusCode() >= 300) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "TRACE_STORE_ERROR", "Trace store returned HTTP " + res.statusCode());
      }
      return json.readTree(res.body());
    } catch (IOException e) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "TRACE_STORE_ERROR", "Trace store unavailable.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.BAD_GATEWAY, "TRACE_STORE_ERROR", "Interrupted.");
    }
  }

  /** TraceQL for spans of an operation: its name is the operation, or it calls that host/route. */
  static String selector(Hop hop, boolean errorsOnly) {
    StringBuilder q = new StringBuilder("{ resource.service.name = \"").append(escape(hop.service())).append("\"");
    if (hop.operation() != null && !hop.operation().isBlank()) {
      q.append(" && name = \"").append(escape(hop.operation())).append("\"");
    }
    if (errorsOnly) {
      q.append(" && status = error");
    }
    return q.append(" }").toString();
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static Map<String, String> attributes(JsonNode list) {
    Map<String, String> out = new LinkedHashMap<>();
    for (JsonNode kv : list) {
      JsonNode v = kv.path("value");
      String value =
          v.has("stringValue") ? v.get("stringValue").asText()
              : v.has("intValue") ? v.get("intValue").asText()
              : v.has("doubleValue") ? v.get("doubleValue").asText()
              : v.has("boolValue") ? v.get("boolValue").asText()
              : v.toString();
      out.put(kv.path("key").asText(), value);
    }
    return out;
  }

  /** Tempo's JSON gives span ids base64-encoded; everything else speaks hex. */
  private static String hex(String base64) {
    try {
      return HexFormat.of().formatHex(Base64.getDecoder().decode(base64));
    } catch (IllegalArgumentException e) {
      return base64;
    }
  }
}
