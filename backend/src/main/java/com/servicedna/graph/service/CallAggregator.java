package com.servicedna.graph.service;

import com.servicedna.graph.domain.CallKey;
import com.servicedna.graph.domain.CallStats;
import com.servicedna.graph.domain.ObservedCall;
import com.servicedna.graph.repository.ObservedCallRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Accumulates observed calls in memory per minute and writes them to {@code observed_calls} in one
 * transaction every few seconds, so ingestion never writes a row per span.
 */
@Component
public class CallAggregator {

  private static final Logger log = LoggerFactory.getLogger(CallAggregator.class);

  record BucketKey(UUID organizationId, OffsetDateTime bucketStart, CallKey key) {}

  private final ObservedCallRepository repository;
  private final TransactionTemplate transactions;
  // Recorders share the read lock; flush takes the write lock only to swap in a fresh map, so no
  // count can land in a map that's already being written out.
  private final ReadWriteLock swapLock = new ReentrantReadWriteLock();
  private Map<BucketKey, CallStats> pending = new ConcurrentHashMap<>();

  public CallAggregator(ObservedCallRepository repository, TransactionTemplate transactions) {
    this.repository = repository;
    this.transactions = transactions;
  }

  public void record(UUID organizationId, Instant at, CallKey key, long durationMs, boolean error) {
    OffsetDateTime minute = OffsetDateTime.ofInstant(at, ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
    swapLock.readLock().lock();
    try {
      pending.computeIfAbsent(new BucketKey(organizationId, minute, key), k -> new CallStats()).record(durationMs, error);
    } finally {
      swapLock.readLock().unlock();
    }
  }

  @Scheduled(fixedDelayString = "${graph.flush-interval-ms:10000}")
  public void flush() {
    Map<BucketKey, CallStats> batch;
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
    try {
      transactions.executeWithoutResult(status -> batch.forEach(this::upsert));
    } catch (DataIntegrityViolationException e) {
      // Another instance inserted one of these rows first; retry row by row so the rest land.
      batch.forEach((k, v) -> {
        try {
          transactions.executeWithoutResult(status -> upsert(k, v));
        } catch (RuntimeException retryFailure) {
          log.warn("Dropping observed calls for {}: {}", k.key(), retryFailure.getMessage());
        }
      });
    }
  }

  private void upsert(BucketKey bucket, CallStats stats) {
    CallKey key = bucket.key();
    ObservedCall row =
        repository
            .findByOrganizationIdAndBucketStartAndSourceServiceIdAndSourceOperationAndTargetKindAndTargetNameAndTargetOperationAndProtocol(
                bucket.organizationId(),
                bucket.bucketStart(),
                key.sourceServiceId(),
                key.sourceOperation(),
                key.targetKind(),
                key.targetName(),
                key.targetOperation(),
                key.protocol())
            .orElseGet(() -> new ObservedCall(bucket.organizationId(), bucket.bucketStart(), key));
    row.add(stats);
    repository.save(row);
  }
}
