package com.servicedna.ingestion.domain;

import com.servicedna.organization.domain.Organization;
import com.servicedna.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** An organization-wide telemetry key. Only its hash is stored. */
@Entity
@Table(name = "ingestion_keys")
public class IngestionKey {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "key_hash", nullable = false, unique = true, length = 64)
  private String keyHash;

  /** The key's first characters, shown in the UI so keys can be told apart. */
  @Column(name = "key_prefix", nullable = false, length = 16)
  private String keyPrefix;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by")
  private User createdBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "last_used_at")
  private OffsetDateTime lastUsedAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  public IngestionKey() {}

  public IngestionKey(
      UUID id, Organization organization, String name, String keyHash, String keyPrefix, User createdBy) {
    this.id = id;
    this.organization = organization;
    this.name = name;
    this.keyHash = keyHash;
    this.keyPrefix = keyPrefix;
    this.createdBy = createdBy;
  }

  public UUID getId() {
    return id;
  }

  public Organization getOrganization() {
    return organization;
  }

  public String getName() {
    return name;
  }

  public String getKeyPrefix() {
    return keyPrefix;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(OffsetDateTime lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }

  public OffsetDateTime getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(OffsetDateTime revokedAt) {
    this.revokedAt = revokedAt;
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }
}
