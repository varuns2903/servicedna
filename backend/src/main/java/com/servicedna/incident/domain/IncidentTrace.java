package com.servicedna.incident.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A trace attached to an incident, with its hops snapshotted for the post-mortem. */
@Entity
@Table(name = "incident_traces")
public class IncidentTrace {

  @Id private UUID id;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Column(name = "trace_id", nullable = false, length = 32)
  private String traceId;

  @Column(length = 1000)
  private String note;

  @Column(nullable = false, length = 500)
  private String summary;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String hops;

  @Column(name = "attached_by")
  private UUID attachedBy;

  @Column(name = "attached_at", nullable = false)
  private OffsetDateTime attachedAt;

  protected IncidentTrace() {}

  public IncidentTrace(UUID incidentId, String traceId, String note, String summary, String hops, UUID attachedBy) {
    this.id = UUID.randomUUID();
    this.incidentId = incidentId;
    this.traceId = traceId;
    this.note = note;
    this.summary = summary;
    this.hops = hops;
    this.attachedBy = attachedBy;
    this.attachedAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getIncidentId() {
    return incidentId;
  }

  public String getTraceId() {
    return traceId;
  }

  public String getNote() {
    return note;
  }

  public String getSummary() {
    return summary;
  }

  public String getHops() {
    return hops;
  }

  public UUID getAttachedBy() {
    return attachedBy;
  }

  public OffsetDateTime getAttachedAt() {
    return attachedAt;
  }
}
