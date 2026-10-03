package com.servicedna.telemetry.requests;

import com.servicedna.ingestion.service.ServiceDiscoveryListener;
import com.servicedna.service.dto.TelemetryIdentity;
import com.servicedna.ingestion.event.TraceBatchReceivedEvent;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Each service's request rate, error rate and latency from its traces: every server and consumer
 * span counts as a request it handled (an error when the span's status is ERROR). Counted in
 * memory per service and minute, and added to {@code service_request_stats} every few seconds.
 */
@Component
public class RequestStats {

  private static final Logger log = LoggerFactory.getLogger(RequestStats.class);

  /** Upper bounds of the latency histogram's buckets, in ms; one more bucket holds the rest. */
  static final long[] BOUNDS_MS = {10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000};
  private static final String[] COLUMNS = {"le_10", "le_25", "le_50", "le_100", "le_250", "le_500", "le_1000", "le_2500",
      "le_5000", "le_10000", "le_inf"};
  private static final String INCREMENT = "UPDATE service_request_stats SET requests = requests + ?, errors = errors + ?, "
      + "duration_max_ms = GREATEST(duration_max_ms, ?), "
      + Arrays.stream(COLUMNS).map(c -> c + " = " + c + " + ?").collect(Collectors.joining(", "))
      + " WHERE service_id = ? AND bucket_start = ?";
  private static final String INSERT = "INSERT INTO service_request_stats (service_id, bucket_start, requests, errors, duration_max_ms, "
      + String.join(", ", COLUMNS) + ") VALUES (?, ?, ?, ?, ?" + ", ?".repeat(COLUMNS.length) + ")";

  /** Totals over a window: request and error counts, the slowest request, and the histogram. */
  public record Window(long requests, long errors, long maxMs, long[] buckets) {

    public double errorRatePercent() {
      return requests == 0 ? 0 : errors * 100.0 / requests;
    }

    /** The p-th percentile latency in ms, interpolated within its histogram bucket. */
    public double percentileMs(double p) {
      long rank = (long) Math.ceil(requests * p / 100.0);
      long seen = 0;
      for (int i = 0; i < buckets.length; i++) {
        if (buckets[i] > 0 && seen + buckets[i] >= rank) {
          double lower = i == 0 ? 0 : BOUNDS_MS[i - 1];
          double upper = i < BOUNDS_MS.length ? BOUNDS_MS[i] : Math.max(lower, maxMs);
          return lower + (upper - lower) * (rank - seen) / buckets[i];
        }
        seen += buckets[i];
      }
      return maxMs;
    }
  }

  record Key(UUID serviceId, OffsetDateTime minute) {}

  static final class Counts {
    long requests;
    long errors;
    long maxMs;
    final long[] buckets = new long[COLUMNS.length];

    synchronized void add(long durationMs, boolean error) {
      requests++;
      if (error) {
        errors++;
      }
      maxMs = Math.max(maxMs, durationMs);
      int i = 0;
      while (i < BOUNDS_MS.length && durationMs > BOUNDS_MS[i]) {
        i++;
      }
      buckets[i]++;
    }
  }

  private final ServiceDiscoveryListener serviceDiscovery;
  private final JdbcTemplate jdbc;
  private final ReadWriteLock swapLock = new ReentrantReadWriteLock();
  private Map<Key, Counts> pending = new ConcurrentHashMap<>();

  public RequestStats(ServiceDiscoveryListener serviceDiscovery, JdbcTemplate jdbc) {
    this.serviceDiscovery = serviceDiscovery;
    this.jdbc = jdbc;
  }

  @EventListener
  public void onTraceBatch(TraceBatchReceivedEvent event) {
    try {
      for (ResourceSpans resourceSpans : event.request().getResourceSpansList()) {
        TelemetryIdentity identity = ServiceDiscoveryListener.identityOf(resourceSpans.getResource().getAttributesList());
        if (identity == null) {
          continue;
        }
        Optional<UUID> serviceId = serviceDiscovery.register(event.organizationId(), identity);
        if (serviceId.isEmpty()) {
          continue;
        }
        for (ScopeSpans scope : resourceSpans.getScopeSpansList()) {
          for (Span span : scope.getSpansList()) {
            if (span.getKind() == Span.SpanKind.SPAN_KIND_SERVER || span.getKind() == Span.SpanKind.SPAN_KIND_CONSUMER) {
              record(serviceId.get(), span);
            }
          }
        }
      }
    } catch (RuntimeException e) {
      // Best-effort, like the call graph: never fail the export over it.
      log.warn("Counting requests failed: {}", e.getMessage());
    }
  }

  private void record(UUID serviceId, Span span) {
    Instant start = Instant.ofEpochSecond(0, span.getStartTimeUnixNano());
    OffsetDateTime minute = OffsetDateTime.ofInstant(start, ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
    long durationMs = Math.max(0, (span.getEndTimeUnixNano() - span.getStartTimeUnixNano()) / 1_000_000);
    boolean error = span.getStatus().getCode() == Status.StatusCode.STATUS_CODE_ERROR;
    swapLock.readLock().lock();
    try {
      pending.computeIfAbsent(new Key(serviceId, minute), k -> new Counts()).add(durationMs, error);
    } finally {
      swapLock.readLock().unlock();
    }
  }

  @Scheduled(fixedDelayString = "${graph.flush-interval-ms:10000}")
  public void flush() {
    Map<Key, Counts> batch;
    swapLock.writeLock().lock();
    try {
      if (pending.isEmpty()) {
        return;
      }
      batch = pending;
      pending = new ConcurrentHashMap<>();
    } finally {
      swapLock.writeLock().unlock();
    }
    batch.forEach((key, counts) -> {
      try {
        add(key, counts);
      } catch (RuntimeException e) {
        log.warn("Dropping request counts for service {}: {}", key.serviceId(), e.getMessage());
      }
    });
  }

  /** Adds to the row, creating it if needed; increments are atomic, so instances can share it. */
  private void add(Key key, Counts c) {
    Object[] increment = new Object[3 + COLUMNS.length + 2];
    increment[0] = c.requests;
    increment[1] = c.errors;
    increment[2] = c.maxMs;
    for (int i = 0; i < COLUMNS.length; i++) {
      increment[3 + i] = c.buckets[i];
    }
    increment[3 + COLUMNS.length] = key.serviceId();
    increment[4 + COLUMNS.length] = key.minute();
    if (jdbc.update(INCREMENT, increment) > 0) {
      return;
    }
    Object[] insert = new Object[5 + COLUMNS.length];
    insert[0] = key.serviceId();
    insert[1] = key.minute();
    insert[2] = c.requests;
    insert[3] = c.errors;
    insert[4] = c.maxMs;
    for (int i = 0; i < COLUMNS.length; i++) {
      insert[5 + i] = c.buckets[i];
    }
    try {
      jdbc.update(INSERT, insert);
    } catch (DuplicateKeyException e) {
      jdbc.update(INCREMENT, increment); // another instance created it first
    }
  }

  /** Totals for a service since a time (whole minutes). */
  public Window window(UUID serviceId, OffsetDateTime since) {
    OffsetDateTime from = since.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
    String sums = "COALESCE(SUM(requests), 0), COALESCE(SUM(errors), 0), COALESCE(MAX(duration_max_ms), 0), "
        + Arrays.stream(COLUMNS).map(c -> "COALESCE(SUM(" + c + "), 0)").collect(Collectors.joining(", "));
    return jdbc.queryForObject("SELECT " + sums + " FROM service_request_stats WHERE service_id = ? AND bucket_start >= ?",
        (rs, n) -> {
          long[] buckets = new long[COLUMNS.length];
          for (int i = 0; i < COLUMNS.length; i++) {
            buckets[i] = rs.getLong(4 + i);
          }
          return new Window(rs.getLong(1), rs.getLong(2), rs.getLong(3), buckets);
        }, serviceId, from);
  }

  /** Request stats are for alerting on recent traffic; a week is plenty. */
  @Scheduled(cron = "${PING_RETENTION_CRON:0 0 3 * * *}")
  public void prune() {
    int removed = jdbc.update("DELETE FROM service_request_stats WHERE bucket_start < ?", OffsetDateTime.now(ZoneOffset.UTC).minusDays(7));
    if (removed > 0) {
      log.info("Pruned {} request-stats rows older than 7 days", removed);
    }
  }
}
