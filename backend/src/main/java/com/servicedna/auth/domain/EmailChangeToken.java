package com.servicedna.auth.domain;

import com.servicedna.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "email_change_tokens")
public class EmailChangeToken {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "new_email", nullable = false)
  private String newEmail;

  @Column(nullable = false, unique = true)
  private String token;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  public EmailChangeToken() {}

  public EmailChangeToken(UUID id, User user, String newEmail, String token, Instant expiresAt) {
    this.id = id;
    this.user = user;
    this.newEmail = newEmail;
    this.token = token;
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getNewEmail() {
    return newEmail;
  }

  public String getToken() {
    return token;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
