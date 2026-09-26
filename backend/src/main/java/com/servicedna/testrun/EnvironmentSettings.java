package com.servicedna.testrun;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "environment_settings")
public class EnvironmentSettings {

  /** A plain class rather than a record: this Hibernate version can't populate embeddable records. */
  @Embeddable
  public static class Key implements Serializable {
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "environment")
    private String environment;

    protected Key() {}

    public Key(UUID organizationId, String environment) {
      this.organizationId = organizationId;
      this.environment = environment;
    }

    public UUID organizationId() { return organizationId; }

    public String environment() { return environment; }

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && java.util.Objects.equals(organizationId, k.organizationId) && java.util.Objects.equals(environment, k.environment);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(organizationId, environment);
    }
  }

  @EmbeddedId private Key id;

  @Column(name = "allow_test_runs", nullable = false)
  private boolean allowTestRuns;

  @Column(name = "updated_by")
  private UUID updatedBy;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public EnvironmentSettings() {}

  public EnvironmentSettings(UUID organizationId, String environment) {
    this.id = new Key(organizationId, environment);
  }

  public Key getId() { return id; }
  public boolean isAllowTestRuns() { return allowTestRuns; }
  public void setAllowTestRuns(boolean allowTestRuns) { this.allowTestRuns = allowTestRuns; }
  public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
