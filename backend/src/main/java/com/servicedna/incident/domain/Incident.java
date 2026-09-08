package com.servicedna.incident.domain;

import com.servicedna.organization.domain.Organization;
import com.servicedna.service.domain.Service;
import com.servicedna.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "incidents")
public class Incident {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by", nullable = false)
  private User createdBy;

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private IncidentStatus status = IncidentStatus.INVESTIGATING;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private IncidentSeverity severity = IncidentSeverity.MINOR;

  @ManyToMany
  @JoinTable(
      name = "incident_services",
      joinColumns = @JoinColumn(name = "incident_id"),
      inverseJoinColumns = @JoinColumn(name = "service_id"))
  private Set<Service> affectedServices = new HashSet<>();

  @Column(name = "resolved_at")
  private OffsetDateTime resolvedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public Incident() {}

  public Incident(
      UUID id,
      Organization organization,
      User createdBy,
      String title,
      String description,
      IncidentSeverity severity) {
    this.id = id;
    this.organization = organization;
    this.createdBy = createdBy;
    this.title = title;
    this.description = description;
    this.severity = severity != null ? severity : IncidentSeverity.MINOR;
    this.status = IncidentStatus.INVESTIGATING;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Organization getOrganization() {
    return organization;
  }

  public void setOrganization(Organization organization) {
    this.organization = organization;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(User createdBy) {
    this.createdBy = createdBy;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public IncidentStatus getStatus() {
    return status;
  }

  public void setStatus(IncidentStatus status) {
    this.status = status;
  }

  public IncidentSeverity getSeverity() {
    return severity;
  }

  public void setSeverity(IncidentSeverity severity) {
    this.severity = severity;
  }

  public Set<Service> getAffectedServices() {
    return affectedServices;
  }

  public void setAffectedServices(Set<Service> affectedServices) {
    this.affectedServices = affectedServices;
  }

  public OffsetDateTime getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(OffsetDateTime resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
