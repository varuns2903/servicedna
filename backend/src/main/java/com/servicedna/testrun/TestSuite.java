package com.servicedna.testrun;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** One run of a set of cases; passes when every case's run passes. */
@Entity
@Table(name = "test_suites")
public class TestSuite {

  public static final String RUNNING = "RUNNING";
  public static final String PASSED = "PASSED";
  public static final String FAILED = "FAILED";

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(nullable = false)
  private String name;

  @Column(name = "collection_id")
  private UUID collectionId;

  @Column(length = 64)
  private String environment;

  @Column(nullable = false, length = 16)
  private String status = RUNNING;

  @Column(name = "created_by")
  private UUID createdBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "finished_at")
  private OffsetDateTime finishedAt;

  public TestSuite() {}

  public TestSuite(UUID organizationId, String name, UUID collectionId, String environment, UUID createdBy) {
    this.id = UUID.randomUUID();
    this.organizationId = organizationId;
    this.name = name;
    this.collectionId = collectionId;
    this.environment = environment;
    this.createdBy = createdBy;
  }

  public UUID getId() { return id; }
  public UUID getOrganizationId() { return organizationId; }
  public String getName() { return name; }
  public UUID getCollectionId() { return collectionId; }
  public String getEnvironment() { return environment; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getFinishedAt() { return finishedAt; }
  public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
}
