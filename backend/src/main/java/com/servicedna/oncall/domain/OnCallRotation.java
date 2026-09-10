package com.servicedna.oncall.domain;

import com.servicedna.organization.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "on_call_rotations")
public class OnCallRotation {

  @Id private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", nullable = false, unique = true)
  private Organization organization;

  @Column(name = "rotation_length_days", nullable = false)
  private int rotationLengthDays;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public OnCallRotation() {}

  public OnCallRotation(UUID id, Organization organization, int rotationLengthDays, LocalDate startDate) {
    this.id = id;
    this.organization = organization;
    this.rotationLengthDays = rotationLengthDays;
    this.startDate = startDate;
  }

  public UUID getId() {
    return id;
  }

  public Organization getOrganization() {
    return organization;
  }

  public int getRotationLengthDays() {
    return rotationLengthDays;
  }

  public void setRotationLengthDays(int rotationLengthDays) {
    this.rotationLengthDays = rotationLengthDays;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
