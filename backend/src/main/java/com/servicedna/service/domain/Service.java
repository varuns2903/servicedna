package com.servicedna.service.domain;

import com.servicedna.organization.domain.Organization;
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
@Table(name = "services")
public class Service {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "repository_url")
  private String repositoryUrl;

  @Column(name = "health_check_url")
  private String healthCheckUrl;

  @Column(nullable = false)
  private String region = "global";

  @Column(name = "slo_target_percentage", nullable = false)
  private double sloTargetPercentage = 99.9;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ServiceStatus status = ServiceStatus.UNKNOWN;

  @Column(name = "api_key", nullable = false, unique = true)
  private String apiKey;

  /** From telemetry's deployment.environment; null for services registered without one. */
  @Column(length = 64)
  private String environment;

  /** From telemetry's telemetry.sdk.language. */
  @Column(length = 32)
  private String language;

  /** From telemetry's service.version. */
  @Column(length = 64)
  private String version;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ServiceSource source = ServiceSource.MANUAL;

  @Column(name = "last_telemetry_at")
  private OffsetDateTime lastTelemetryAt;

  @ManyToMany
  @JoinTable(
      name = "service_dependencies",
      joinColumns = @JoinColumn(name = "service_id"),
      inverseJoinColumns = @JoinColumn(name = "depends_on_service_id"))
  private Set<Service> dependencies = new HashSet<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public Service() {}

  public Service(
      UUID id,
      Organization organization,
      String name,
      String description,
      String repositoryUrl,
      String region,
      String apiKey) {
    this(id, organization, name, description, repositoryUrl, region, apiKey, null);
  }

  public Service(
      UUID id,
      Organization organization,
      String name,
      String description,
      String repositoryUrl,
      String region,
      String apiKey,
      String healthCheckUrl) {
    this.id = id;
    this.organization = organization;
    this.name = name;
    this.description = description;
    this.repositoryUrl = repositoryUrl;
    this.region = region != null ? region : "global";
    this.apiKey = apiKey;
    this.healthCheckUrl = healthCheckUrl;
    this.status = ServiceStatus.UNKNOWN;
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  /** Team or person responsible, from servicedna.yaml. */
  @Column(name = "owner")
  private String owner;

  /** critical, high, medium or low, from servicedna.yaml. */
  @Column(name = "tier", length = 16)
  private String tier;

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public String getTier() {
    return tier;
  }

  public void setTier(String tier) {
    this.tier = tier;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getRepositoryUrl() {
    return repositoryUrl;
  }

  public void setRepositoryUrl(String repositoryUrl) {
    this.repositoryUrl = repositoryUrl;
  }

  public String getHealthCheckUrl() {
    return healthCheckUrl;
  }

  public void setHealthCheckUrl(String healthCheckUrl) {
    this.healthCheckUrl = healthCheckUrl;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public double getSloTargetPercentage() {
    return sloTargetPercentage;
  }

  public void setSloTargetPercentage(double sloTargetPercentage) {
    this.sloTargetPercentage = sloTargetPercentage;
  }

  public ServiceStatus getStatus() {
    return status;
  }

  public void setStatus(ServiceStatus status) {
    this.status = status;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public ServiceSource getSource() {
    return source;
  }

  public void setSource(ServiceSource source) {
    this.source = source;
  }

  public OffsetDateTime getLastTelemetryAt() {
    return lastTelemetryAt;
  }

  public void setLastTelemetryAt(OffsetDateTime lastTelemetryAt) {
    this.lastTelemetryAt = lastTelemetryAt;
  }

  public Set<Service> getDependencies() {
    return dependencies;
  }

  public void setDependencies(Set<Service> dependencies) {
    this.dependencies = dependencies;
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
