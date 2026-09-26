package com.servicedna.alert.domain;

import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.organization.domain.Organization;
import com.servicedna.service.domain.Service;
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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "alert_rules")
public class AlertRule {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "service_id", nullable = false)
  private Service service;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AlertCondition condition;

  @Column(name = "webhook_url")
  private String webhookUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "integration_type", nullable = false)
  private IntegrationType integrationType = IntegrationType.GENERIC;

  /** When set, a matching status change opens (or updates) an incident with this severity. */
  @Enumerated(EnumType.STRING)
  @Column(name = "incident_severity")
  private IncidentSeverity incidentSeverity;

  /** Threshold conditions only: ms, percent, or a failure count depending on the condition. */
  private Double threshold;

  /** LATENCY_ABOVE / ERROR_RATE_ABOVE only: how far back to look. */
  @Column(name = "window_minutes")
  private Integer windowMinutes;

  /** Threshold conditions only: whether the threshold is currently exceeded. */
  @Column(nullable = false)
  private boolean breached;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public AlertRule() {}

  public AlertRule(
      UUID id,
      Organization organization,
      Service service,
      AlertCondition condition,
      String webhookUrl,
      IntegrationType integrationType,
      IncidentSeverity incidentSeverity,
      Double threshold,
      Integer windowMinutes) {
    this.id = id;
    this.organization = organization;
    this.service = service;
    this.condition = condition;
    this.webhookUrl = webhookUrl;
    this.integrationType = integrationType != null ? integrationType : IntegrationType.GENERIC;
    this.incidentSeverity = incidentSeverity;
    this.threshold = threshold;
    this.windowMinutes = windowMinutes;
  }

  public Double getThreshold() {
    return threshold;
  }

  public Integer getWindowMinutes() {
    return windowMinutes;
  }

  /** "CATALOG" when servicedna.yaml manages this rule (it's replaced on each scan); null when made by hand. */
  @Column(name = "managed_by", length = 16)
  private String managedBy;

  public String getManagedBy() {
    return managedBy;
  }

  public void setManagedBy(String managedBy) {
    this.managedBy = managedBy;
  }

  public boolean isBreached() {
    return breached;
  }

  public void setBreached(boolean breached) {
    this.breached = breached;
  }

  public IncidentSeverity getIncidentSeverity() {
    return incidentSeverity;
  }

  public void setIncidentSeverity(IncidentSeverity incidentSeverity) {
    this.incidentSeverity = incidentSeverity;
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

  public Service getService() {
    return service;
  }

  public void setService(Service service) {
    this.service = service;
  }

  public AlertCondition getCondition() {
    return condition;
  }

  public void setCondition(AlertCondition condition) {
    this.condition = condition;
  }

  public String getWebhookUrl() {
    return webhookUrl;
  }

  public void setWebhookUrl(String webhookUrl) {
    this.webhookUrl = webhookUrl;
  }

  public IntegrationType getIntegrationType() {
    return integrationType;
  }

  public void setIntegrationType(IntegrationType integrationType) {
    this.integrationType = integrationType;
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
