package com.servicedna.traces;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TraceDto {

  private TraceDto() {}

  public record Summary(
      String traceId, String rootService, String rootOperation, Instant start, long durationMs, boolean error) {}

  /** A trace as a flat list of spans, ordered by start time; parents link them into a tree. */
  public record Trace(String traceId, Instant start, long durationMs, List<Span> spans) {}

  public record Span(
      String spanId,
      String parentSpanId,
      String service,
      String name,
      String kind,
      Instant start,
      double durationMs,
      boolean error,
      String statusMessage,
      Map<String, String> attributes,
      List<Event> events) {}

  public record Event(String name, Instant time, Map<String, String> attributes) {}
}
