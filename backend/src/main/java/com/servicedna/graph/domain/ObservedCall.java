package com.servicedna.graph.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One minute of calls from an operation in one service to an operation on one target. */
@Entity
@Table(
    name = "observed_calls",
    // Mirrors the migration's constraint, so concurrent inserts of one bucket conflict everywhere.
    uniqueConstraints = @UniqueConstraint(name = "uq_observed_calls", columnNames = {
        "organization_id", "bucket_start", "source_service_id", "source_operation",
        "target_kind", "target_name", "target_operation", "protocol"}))
public class ObservedCall {

  /** Upper bounds (ms) of the latency histogram buckets; a final bucket holds anything slower. */
  public static final long[] BUCKET_BOUNDS_MS = {10, 50, 100, 250, 500, 1000, 2500, 5000, 10000};

  @Id private UUID id;

  /** Several instances may add to the same bucket: a stale write fails and is retried. */
  @Version private long version;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "bucket_start", nullable = false)
  private OffsetDateTime bucketStart;

  @Column(name = "source_service_id", nullable = false)
  private UUID sourceServiceId;

  @Column(name = "source_operation", nullable = false)
  private String sourceOperation;

  @Column(name = "target_service_id")
  private UUID targetServiceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_kind", nullable = false, length = 16)
  private TargetKind targetKind;

  @Column(name = "target_name", nullable = false)
  private String targetName;

  @Column(name = "target_operation", nullable = false)
  private String targetOperation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Protocol protocol;

  @Column(nullable = false)
  private long calls;

  @Column(nullable = false)
  private long errors;

  @Column(name = "duration_sum_ms", nullable = false)
  private long durationSumMs;

  @Column(name = "duration_max_ms", nullable = false)
  private long durationMaxMs;

  @Column(name = "le_10", nullable = false) private long le10;
  @Column(name = "le_50", nullable = false) private long le50;
  @Column(name = "le_100", nullable = false) private long le100;
  @Column(name = "le_250", nullable = false) private long le250;
  @Column(name = "le_500", nullable = false) private long le500;
  @Column(name = "le_1000", nullable = false) private long le1000;
  @Column(name = "le_2500", nullable = false) private long le2500;
  @Column(name = "le_5000", nullable = false) private long le5000;
  @Column(name = "le_10000", nullable = false) private long le10000;
  @Column(name = "le_inf", nullable = false) private long leInf;

  public ObservedCall() {}

  public ObservedCall(UUID organizationId, OffsetDateTime bucketStart, CallKey key) {
    this.id = UUID.randomUUID();
    this.organizationId = organizationId;
    this.bucketStart = bucketStart;
    this.sourceServiceId = key.sourceServiceId();
    this.sourceOperation = key.sourceOperation();
    this.targetServiceId = key.targetServiceId();
    this.targetKind = key.targetKind();
    this.targetName = key.targetName();
    this.targetOperation = key.targetOperation();
    this.protocol = key.protocol();
  }

  /** Adds pre-aggregated counts (see {@link CallStats}). */
  public void add(CallStats stats) {
    calls += stats.calls();
    errors += stats.errors();
    durationSumMs += stats.durationSumMs();
    durationMaxMs = Math.max(durationMaxMs, stats.durationMaxMs());
    long[] b = stats.buckets();
    le10 += b[0];
    le50 += b[1];
    le100 += b[2];
    le250 += b[3];
    le500 += b[4];
    le1000 += b[5];
    le2500 += b[6];
    le5000 += b[7];
    le10000 += b[8];
    leInf += b[9];
  }

  public long[] buckets() {
    return new long[] {le10, le50, le100, le250, le500, le1000, le2500, le5000, le10000, leInf};
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public OffsetDateTime getBucketStart() {
    return bucketStart;
  }

  public UUID getSourceServiceId() {
    return sourceServiceId;
  }

  public String getSourceOperation() {
    return sourceOperation;
  }

  public UUID getTargetServiceId() {
    return targetServiceId;
  }

  public TargetKind getTargetKind() {
    return targetKind;
  }

  public String getTargetName() {
    return targetName;
  }

  public String getTargetOperation() {
    return targetOperation;
  }

  public Protocol getProtocol() {
    return protocol;
  }

  public long getCalls() {
    return calls;
  }

  public long getErrors() {
    return errors;
  }

  public long getDurationSumMs() {
    return durationSumMs;
  }

  public long getDurationMaxMs() {
    return durationMaxMs;
  }
}
