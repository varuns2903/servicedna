package com.servicedna.follow;

import com.servicedna.common.exception.ApiException;
import com.servicedna.logs.LogDto;
import com.servicedna.logs.LogQl;
import com.servicedna.logs.LogQueryService;
import com.servicedna.traces.TraceDto;
import com.servicedna.traces.TraceQl;
import com.servicedna.traces.TraceQueryService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Follows a business key (orderId=o-17) through everything that touched it: every trace with a
 * span tagged with it, carrying it as an attribute or Kafka message key, or with it in a captured
 * body — separate traces included, which is what makes this work where trace context was lost —
 * and every log line that mentions it.
 */
@Service
public class FollowService {

  private static final Pattern NAME = Pattern.compile("^[A-Za-z_][\\w.\\-]*$");
  private static final String NOT_ID = "[^A-Za-z0-9_-]";

  private final TraceQueryService traces;
  private final LogQueryService logs;

  public FollowService(TraceQueryService traces, LogQueryService logs) {
    this.traces = traces;
    this.logs = logs;
  }

  public record Story(
      String key, String value, String traceQuery, String logQuery, List<String> services,
      Instant firstSeen, Instant lastSeen, List<TraceDto.Found> traces, List<LogDto.Entry> logs, String logsUnavailable) {}

  public Story follow(UUID organizationId, String key, String value, Instant from, Instant to, UUID userId) {
    if (key == null || !NAME.matcher(key).matches()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "Name the key, e.g. orderId.");
    }
    if (value == null || value.isBlank() || value.length() > 200) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "Give the key's value.");
    }
    String v = value.trim();
    Instant end = to != null ? to : Instant.now();
    Instant start = from != null ? from : end.minus(Duration.ofDays(1));

    String quoted = TraceQl.quote(v);
    // The whole id, not a prefix of another one (o-1 mustn't match o-12): letters, digits, _ and -
    // count as part of an id.
    String literal = v.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0");
    String contains = TraceQl.quote("(.*" + NOT_ID + ")?" + literal + "(" + NOT_ID + ".*)?");
    String traceQuery = "{ span.sdna.key." + key + " = " + quoted
        + " || ." + key + " = " + quoted
        + " || span.sdna.capture." + key + " = " + quoted
        + " || span.messaging.kafka.message.key = " + quoted
        + " || span.sdna.request.body =~ " + contains
        + " || span.sdna.response.body =~ " + contains + " }";
    TraceDto.Explore found = traces.explore(organizationId, null, traceQuery, start, end, 100, userId);

    // Logs are optional: without a log store the traces still tell the story.
    LogDto.Search lines = null;
    String logsUnavailable = null;
    try {
      String logQuery = "{service_name=~\".+\"} |~ " + LogQl.quote("(^|" + NOT_ID + ")" + literal + "(" + NOT_ID + "|$)");
      lines = logs.search(organizationId, null, logQuery, start, end, 500, userId);
    } catch (ApiException e) {
      logsUnavailable = e.getMessage();
    }

    TreeSet<String> services = new TreeSet<>();
    List<Instant> times = new ArrayList<>();
    for (TraceDto.Found t : found.traces()) {
      services.addAll(t.services().keySet());
      times.add(t.start());
    }
    List<LogDto.Entry> entries = lines == null ? List.of() : lines.entries();
    for (LogDto.Entry e : entries) {
      if (e.service() != null) {
        services.add(e.service());
      }
      times.add(e.time());
    }
    return new Story(key, v, found.query(), lines == null ? null : lines.query(), List.copyOf(services),
        times.stream().min(Instant::compareTo).orElse(null), times.stream().max(Instant::compareTo).orElse(null),
        found.traces(), entries, logsUnavailable);
  }
}
