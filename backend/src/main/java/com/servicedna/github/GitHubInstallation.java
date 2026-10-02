package com.servicedna.github;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A GitHub App installation, linked to the ServiceDNA organization whose admin connected it. */
@Entity
@Table(name = "github_installations")
public class GitHubInstallation {

  @Id
  @Column(name = "installation_id")
  private Long installationId;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "account_login", nullable = false)
  private String accountLogin;

  @Column(name = "installed_by", nullable = false)
  private UUID installedBy;

  @Column(name = "installed_at", nullable = false)
  private OffsetDateTime installedAt;

  @Column(name = "last_synced_at")
  private OffsetDateTime lastSyncedAt;

  protected GitHubInstallation() {}

  public GitHubInstallation(long installationId, UUID organizationId, String accountLogin, UUID installedBy) {
    this.installationId = installationId;
    this.organizationId = organizationId;
    this.accountLogin = accountLogin;
    this.installedBy = installedBy;
    this.installedAt = OffsetDateTime.now();
  }

  public long getInstallationId() {
    return installationId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getAccountLogin() {
    return accountLogin;
  }

  public UUID getInstalledBy() {
    return installedBy;
  }

  public OffsetDateTime getInstalledAt() {
    return installedAt;
  }

  public OffsetDateTime getLastSyncedAt() {
    return lastSyncedAt;
  }

  public void setLastSyncedAt(OffsetDateTime lastSyncedAt) {
    this.lastSyncedAt = lastSyncedAt;
  }
}
