package com.servicedna.incident.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "incident_post_mortems")
public class IncidentPostMortem {

  @Id private UUID id;

  @OneToOne
  @JoinColumn(name = "incident_id", nullable = false, unique = true)
  private Incident incident;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String rootCause;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String timeline;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String actionItems;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected IncidentPostMortem() {}

  public IncidentPostMortem(
      UUID id, Incident incident, String rootCause, String timeline, String actionItems) {
    this.id = id;
    this.incident = incident;
    this.rootCause = rootCause;
    this.timeline = timeline;
    this.actionItems = actionItems;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Incident getIncident() {
    return incident;
  }

  public void setIncident(Incident incident) {
    this.incident = incident;
  }

  public String getRootCause() {
    return rootCause;
  }

  public void setRootCause(String rootCause) {
    this.rootCause = rootCause;
  }

  public String getTimeline() {
    return timeline;
  }

  public void setTimeline(String timeline) {
    this.timeline = timeline;
  }

  public String getActionItems() {
    return actionItems;
  }

  public void setActionItems(String actionItems) {
    this.actionItems = actionItems;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
