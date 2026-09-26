package com.servicedna.logs;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class LogDto {

  private LogDto() {}

  /** Search results, newest first, with the LogQL they came from. */
  public record Search(String query, List<Entry> entries) {}

  public record Entry(
      Instant time,
      String service,
      String environment,
      String level,
      String body,
      String traceId,
      String spanId,
      Map<String, String> attributes) {}
}
