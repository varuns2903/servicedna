package com.servicedna.telemetry.requests;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** A row of {@code service_request_stats}; {@link RequestStats} reads and writes them with SQL. */
@Entity
@Table(name = "service_request_stats")
@IdClass(ServiceRequestStatsRow.Key.class)
class ServiceRequestStatsRow {

  public static class Key implements Serializable {
    private UUID serviceId;
    private OffsetDateTime bucketStart;

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && Objects.equals(serviceId, k.serviceId) && Objects.equals(bucketStart, k.bucketStart);
    }

    @Override
    public int hashCode() {
      return Objects.hash(serviceId, bucketStart);
    }
  }

  @Id @Column(name = "service_id") private UUID serviceId;
  @Id @Column(name = "bucket_start") private OffsetDateTime bucketStart;
  @Column(nullable = false) private long requests;
  @Column(nullable = false) private long errors;
  @Column(name = "duration_max_ms", nullable = false) private long durationMaxMs;
  @Column(name = "le_10", nullable = false) private long le10;
  @Column(name = "le_25", nullable = false) private long le25;
  @Column(name = "le_50", nullable = false) private long le50;
  @Column(name = "le_100", nullable = false) private long le100;
  @Column(name = "le_250", nullable = false) private long le250;
  @Column(name = "le_500", nullable = false) private long le500;
  @Column(name = "le_1000", nullable = false) private long le1000;
  @Column(name = "le_2500", nullable = false) private long le2500;
  @Column(name = "le_5000", nullable = false) private long le5000;
  @Column(name = "le_10000", nullable = false) private long le10000;
  @Column(name = "le_inf", nullable = false) private long leInf;

  protected ServiceRequestStatsRow() {}
}
