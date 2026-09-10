package com.servicedna.oncall.domain;

import com.servicedna.organization.domain.OrganizationMember;
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

@Entity
@Table(name = "on_call_rotation_members")
public class OnCallRotationMember {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "rotation_id", nullable = false)
  private OnCallRotation rotation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_member_id", nullable = false)
  private OrganizationMember organizationMember;

  @Column(nullable = false)
  private int position;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  public OnCallRotationMember() {}

  public OnCallRotationMember(
      UUID id, OnCallRotation rotation, OrganizationMember organizationMember, int position) {
    this.id = id;
    this.rotation = rotation;
    this.organizationMember = organizationMember;
    this.position = position;
  }

  public UUID getId() {
    return id;
  }

  public OnCallRotation getRotation() {
    return rotation;
  }

  public OrganizationMember getOrganizationMember() {
    return organizationMember;
  }

  public int getPosition() {
    return position;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
