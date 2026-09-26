package com.servicedna.graph.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** A service-to-service call that has been seen at least once. */
@Entity
@Table(name = "seen_service_edges")
@IdClass(SeenServiceEdge.Key.class)
public class SeenServiceEdge {

  @Id
  @Column(name = "source_service_id")
  private UUID sourceServiceId;

  @Id
  @Column(name = "target_service_id")
  private UUID targetServiceId;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "first_seen_at", nullable = false)
  private OffsetDateTime firstSeenAt;

  protected SeenServiceEdge() {}

  public SeenServiceEdge(UUID organizationId, UUID sourceServiceId, UUID targetServiceId, OffsetDateTime firstSeenAt) {
    this.organizationId = organizationId;
    this.sourceServiceId = sourceServiceId;
    this.targetServiceId = targetServiceId;
    this.firstSeenAt = firstSeenAt;
  }

  public static class Key implements Serializable {
    private UUID sourceServiceId;
    private UUID targetServiceId;

    public Key() {}

    public Key(UUID sourceServiceId, UUID targetServiceId) {
      this.sourceServiceId = sourceServiceId;
      this.targetServiceId = targetServiceId;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && Objects.equals(sourceServiceId, k.sourceServiceId) && Objects.equals(targetServiceId, k.targetServiceId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(sourceServiceId, targetServiceId);
    }
  }
}
