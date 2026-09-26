package com.servicedna.logs;

import com.fasterxml.jackson.databind.JsonNode;
import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.service.OrganizationService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LogQueryService {

  /** Loki's own fields and resource noise, kept out of an entry's attributes. */
  private static final Set<String> RESERVED = Set.of(
      "service_name", "deployment_environment_name", "deployment_environment", "detected_level", "severity_text",
      "severity_number", "trace_id", "span_id", "flags", "observed_timestamp", "service_instance_id", "service_version",
      "service_namespace", "scope_name", "scope_version");
  private static final List<String> NOISE_PREFIXES = List.of("process_", "host_", "os_", "telemetry_", "servicedna_", "container_");

  private final LogStore store;
  private final OrganizationService organizationService;

  public LogQueryService(LogStore store, OrganizationService organizationService) {
    this.store = store;
    this.organizationService = organizationService;
  }

  /** Log lines matching the filters (or raw LogQL), newest first. */
  public LogDto.Search search(UUID organizationId, LogQl.Filters filters, String logql, Instant from, Instant to, int limit, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    String query = logql != null && !logql.isBlank() ? logql.trim() : LogQl.query(filters);
    Instant end = to != null ? to : Instant.now();
    Instant start = from != null ? from : end.minus(Duration.ofHours(1));
    if (!start.isBefore(end)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "The time range is empty.");
    }
    if (Duration.between(start, end).toDays() > 30) {
      start = end.minus(Duration.ofDays(30));
    }
    JsonNode body = store.get(organizationId, "/loki/api/v1/query_range?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
        + "&start=" + nanos(start) + "&end=" + nanos(end) + "&limit=" + Math.max(1, Math.min(limit, 1000)) + "&direction=backward");

    List<LogDto.Entry> entries = new ArrayList<>();
    for (JsonNode stream : body.path("data").path("result")) {
      Map<String, String> labels = new LinkedHashMap<>();
      stream.path("stream").fields().forEachRemaining(e -> labels.put(e.getKey(), e.getValue().asText()));
      Map<String, String> attributes = new LinkedHashMap<>();
      labels.forEach((k, v) -> {
        if (!RESERVED.contains(k) && NOISE_PREFIXES.stream().noneMatch(k::startsWith)) {
          attributes.put(k, v);
        }
      });
      String level = labels.getOrDefault("severity_text", labels.get("detected_level"));
      for (JsonNode value : stream.path("values")) {
        long ns = value.path(0).asLong();
        entries.add(new LogDto.Entry(
            Instant.ofEpochSecond(0, ns), labels.get("service_name"),
            labels.getOrDefault("deployment_environment_name", labels.get("deployment_environment")),
            level == null ? null : level.toLowerCase(), value.path(1).asText(),
            labels.get("trace_id"), labels.get("span_id"), attributes));
      }
    }
    entries.sort(Comparator.comparing(LogDto.Entry::time).reversed());
    return new LogDto.Search(query, entries.size() > limit ? entries.subList(0, limit) : entries);
  }

  private static String nanos(Instant t) {
    return t.getEpochSecond() + String.format("%09d", t.getNano());
  }
}
