package com.servicedna.github;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** A "ServiceDNA" check run on a pull request, waiting for the flow suites it started. */
@Entity
@Table(name = "github_check_runs")
public class GitHubCheckRun {

  @Id private UUID id;

  @Column(name = "installation_id", nullable = false)
  private Long installationId;

  @Column(nullable = false)
  private String repository;

  @Column(name = "check_run_id", nullable = false)
  private Long checkRunId;

  @Column(name = "suite_ids", columnDefinition = "TEXT", nullable = false)
  private String suiteIds;

  /** What was checked before the flows ran (servicedna.yaml), shown above their results. */
  @Column(columnDefinition = "TEXT")
  private String summary;

  @Column(nullable = false)
  private boolean completed;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected GitHubCheckRun() {}

  public GitHubCheckRun(long installationId, String repository, long checkRunId, List<UUID> suiteIds, String summary) {
    this.id = UUID.randomUUID();
    this.installationId = installationId;
    this.repository = repository;
    this.checkRunId = checkRunId;
    this.suiteIds = String.join(",", suiteIds.stream().map(UUID::toString).toList());
    this.summary = summary;
    this.createdAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public long getInstallationId() {
    return installationId;
  }

  public String getRepository() {
    return repository;
  }

  public long getCheckRunId() {
    return checkRunId;
  }

  public List<UUID> getSuiteIds() {
    return Arrays.stream(suiteIds.split(",")).filter(s -> !s.isBlank()).map(UUID::fromString).toList();
  }

  public String getSummary() {
    return summary;
  }

  public boolean isCompleted() {
    return completed;
  }
}
