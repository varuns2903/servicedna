package com.servicedna.testrun;

import com.servicedna.graph.domain.Protocol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "test_runs")
public class TestRun {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(length = 64)
  private String environment;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Protocol protocol;

  @Column(name = "target_service_id")
  private UUID targetServiceId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String target;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String request;

  @Column(name = "trace_id", nullable = false, length = 32)
  private String traceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private TestRunStatus status = TestRunStatus.QUEUED;

  @Column(columnDefinition = "TEXT")
  private String result;

  @Column(columnDefinition = "TEXT")
  private String error;

  @Column(length = 100)
  private String runner;

  @Column(name = "created_by")
  private UUID createdBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "claimed_at")
  private OffsetDateTime claimedAt;

  @Column(name = "responded_at")
  private OffsetDateTime respondedAt;

  @Column(name = "finished_at")
  private OffsetDateTime finishedAt;

  public TestRun() {}

  public TestRun(
      UUID organizationId, String environment, Protocol protocol, UUID targetServiceId, String target, String request, String traceId, UUID createdBy) {
    this.id = UUID.randomUUID();
    this.organizationId = organizationId;
    this.environment = environment;
    this.protocol = protocol;
    this.targetServiceId = targetServiceId;
    this.target = target;
    this.request = request;
    this.traceId = traceId;
    this.createdBy = createdBy;
  }

  public UUID getId() { return id; }
  public UUID getOrganizationId() { return organizationId; }
  public String getEnvironment() { return environment; }
  public Protocol getProtocol() { return protocol; }
  public UUID getTargetServiceId() { return targetServiceId; }
  public String getTarget() { return target; }
  public String getRequest() { return request; }
  public String getTraceId() { return traceId; }
  public TestRunStatus getStatus() { return status; }
  public void setStatus(TestRunStatus status) { this.status = status; }
  public String getResult() { return result; }
  public void setResult(String result) { this.result = result; }
  public String getError() { return error; }
  public void setError(String error) { this.error = error; }
  public String getRunner() { return runner; }
  public UUID getCreatedBy() { return createdBy; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getClaimedAt() { return claimedAt; }
  public OffsetDateTime getRespondedAt() { return respondedAt; }
  public void setRespondedAt(OffsetDateTime respondedAt) { this.respondedAt = respondedAt; }
  public OffsetDateTime getFinishedAt() { return finishedAt; }
  public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
}
