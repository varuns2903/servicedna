package com.servicedna.auth.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "api_tokens")
public class ApiToken {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "token_prefix", nullable = false, length = 20)
  private String tokenPrefix;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  @Column(name = "last_used_at")
  private OffsetDateTime lastUsedAt;

  protected ApiToken() {}

  public ApiToken(UUID userId, String name, String tokenHash, String tokenPrefix, OffsetDateTime expiresAt) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.name = name;
    this.tokenHash = tokenHash;
    this.tokenPrefix = tokenPrefix;
    this.createdAt = OffsetDateTime.now();
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public String getTokenPrefix() {
    return tokenPrefix;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public OffsetDateTime getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(OffsetDateTime lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }

  public boolean isExpired() {
    return expiresAt != null && expiresAt.isBefore(OffsetDateTime.now());
  }
}
