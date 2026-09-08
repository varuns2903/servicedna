package com.servicedna.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "organization_invites")
public class OrganizationInvite {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @Column(nullable = false)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrganizationRole role;

  @Column(nullable = false, unique = true)
  private String token;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InviteStatus status = InviteStatus.PENDING;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public OrganizationInvite() {}

  public OrganizationInvite(
      UUID id,
      Organization organization,
      String email,
      OrganizationRole role,
      String token,
      OffsetDateTime expiresAt) {
    this.id = id;
    this.organization = organization;
    this.email = email;
    this.role = role;
    this.token = token;
    this.expiresAt = expiresAt;
    this.status = InviteStatus.PENDING;
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

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public OrganizationRole getRole() {
    return role;
  }

  public void setRole(OrganizationRole role) {
    this.role = role;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public InviteStatus getStatus() {
    return status;
  }

  public void setStatus(InviteStatus status) {
    this.status = status;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
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
