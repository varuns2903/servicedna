package com.servicedna.testrun;

import com.servicedna.common.exception.ApiException;
import com.servicedna.traces.TraceQueryService;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Decides when a run is over. The entry call returning isn't enough: asynchronous work (Kafka
 * consumers, background calls) keeps adding spans. A run completes once its trace has stopped
 * growing for {@code settle} (and at least {@code minWait} after the response), or after
 * {@code maxWait} regardless.
 */
@Component
public class TestRunCompleter {

  private record Progress(int spans, Instant since) {}

  private final TestRunRepository repository;
  private final TraceQueryService traces;
  private final TransactionTemplate transactions;
  private final Duration settle;
  private final Duration minWait;
  private final Duration maxWait;
  private final Map<UUID, Progress> progress = new ConcurrentHashMap<>();

  public TestRunCompleter(
      TestRunRepository repository,
      TraceQueryService traces,
      TransactionTemplate transactions,
      @Value("${test-runs.settle-ms:5000}") long settleMs,
      @Value("${test-runs.min-wait-ms:3000}") long minWaitMs,
      @Value("${test-runs.max-wait-ms:60000}") long maxWaitMs) {
    this.repository = repository;
    this.traces = traces;
    this.transactions = transactions;
    this.settle = Duration.ofMillis(settleMs);
    this.minWait = Duration.ofMillis(minWaitMs);
    this.maxWait = Duration.ofMillis(maxWaitMs);
  }

  @Scheduled(fixedDelayString = "${test-runs.complete-check-ms:1000}")
  public void completeSettledRuns() {
    Instant now = Instant.now();
    for (TestRun run : repository.findByStatus(TestRunStatus.WAITING)) {
      Instant responded = run.getRespondedAt() != null ? run.getRespondedAt().toInstant() : now;
      int spans = spanCount(run);
      Progress previous = progress.get(run.getId());
      if (previous == null || previous.spans() != spans) {
        progress.put(run.getId(), new Progress(spans, now));
        previous = progress.get(run.getId());
      }
      boolean settled = spans > 0 && !now.isBefore(previous.since().plus(settle)) && !now.isBefore(responded.plus(minWait));
      boolean expired = !now.isBefore(responded.plus(maxWait));
      if (settled || expired) {
        complete(run.getId(), spans == 0 ? "No spans arrived: are the services instrumented with a ServiceDNA SDK or OpenTelemetry?" : null);
        progress.remove(run.getId());
      }
    }
  }

  private int spanCount(TestRun run) {
    try {
      return traces.trace(run.getOrganizationId(), run.getTraceId()).spans().size();
    } catch (ApiException e) {
      return 0; // not stored yet (or the trace store is down): keep waiting until maxWait
    }
  }

  private void complete(UUID runId, String note) {
    transactions.executeWithoutResult(status -> repository.findById(runId).ifPresent(run -> {
      if (run.getStatus() == TestRunStatus.WAITING) {
        run.setStatus(TestRunStatus.COMPLETED);
        run.setFinishedAt(OffsetDateTime.now());
        if (note != null) {
          run.setError(note);
        }
      }
    }));
  }
}
