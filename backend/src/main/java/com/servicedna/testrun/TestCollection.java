package com.servicedna.testrun;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Saved test cases, like a Postman collection. */
@Entity
@Table(name = "test_collections")
public class TestCollection {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  /** JSON array of cases. */
  @Column(nullable = false, columnDefinition = "TEXT")
  private String cases;

  @Column(name = "created_by")
  private UUID createdBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public TestCollection() {}

  public TestCollection(UUID organizationId, UUID createdBy) {
    this.id = UUID.randomUUID();
    this.organizationId = organizationId;
    this.createdBy = createdBy;
  }

  public UUID getId() { return id; }
  public UUID getOrganizationId() { return organizationId; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public String getCases() { return cases; }
  public void setCases(String cases) { this.cases = cases; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
