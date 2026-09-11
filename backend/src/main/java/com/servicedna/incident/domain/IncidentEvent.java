package com.servicedna.incident.domain;

import com.servicedna.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "incident_events")
public class IncidentEvent {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "incident_id", nullable = false)
  private Incident incident;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false)
  private IncidentEventType eventType;

  @Column(nullable = false, length = 500)
  private String message;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_user_id")
  private User actor;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  public IncidentEvent() {}

  public IncidentEvent(
      UUID id, Incident incident, IncidentEventType eventType, String message, User actor) {
    this.id = id;
    this.incident = incident;
    this.eventType = eventType;
    this.message = message;
    this.actor = actor;
  }

  public UUID getId() {
    return id;
  }

  public Incident getIncident() {
    return incident;
  }

  public IncidentEventType getEventType() {
    return eventType;
  }

  public String getMessage() {
    return message;
  }

  public User getActor() {
    return actor;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
