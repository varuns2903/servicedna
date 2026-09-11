package com.servicedna.escalation.domain;

import com.servicedna.organization.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "escalation_policies")
public class EscalationPolicy {

  @Id private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", nullable = false, unique = true)
  private Organization organization;

  @Column(name = "escalation_email", nullable = false)
  private String escalationEmail;

  @Column(name = "escalate_after_minutes", nullable = false)
  private int escalateAfterMinutes;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public EscalationPolicy() {}

  public EscalationPolicy(
      UUID id, Organization organization, String escalationEmail, int escalateAfterMinutes) {
    this.id = id;
    this.organization = organization;
    this.escalationEmail = escalationEmail;
    this.escalateAfterMinutes = escalateAfterMinutes;
  }

  public UUID getId() {
    return id;
  }

  public Organization getOrganization() {
    return organization;
  }

  public String getEscalationEmail() {
    return escalationEmail;
  }

  public void setEscalationEmail(String escalationEmail) {
    this.escalationEmail = escalationEmail;
  }

  public int getEscalateAfterMinutes() {
    return escalateAfterMinutes;
  }

  public void setEscalateAfterMinutes(int escalateAfterMinutes) {
    this.escalateAfterMinutes = escalateAfterMinutes;
  }
}
