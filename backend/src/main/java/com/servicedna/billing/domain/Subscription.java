package com.servicedna.billing.domain;

import com.servicedna.organization.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "subscriptions")
public class Subscription {

  @Id private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", nullable = false, unique = true)
  private Organization organization;

  @Column(name = "stripe_customer_id")
  private String stripeCustomerId;

  @Column(name = "stripe_subscription_id")
  private String stripeSubscriptionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "plan_type", nullable = false)
  private PlanType planType = PlanType.FREE;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

  @Column(name = "current_period_end")
  private OffsetDateTime currentPeriodEnd;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public Subscription() {}

  public Subscription(UUID id, Organization organization) {
    this.id = id;
    this.organization = organization;
    this.planType = PlanType.FREE;
    this.status = SubscriptionStatus.ACTIVE;
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

  public String getStripeCustomerId() {
    return stripeCustomerId;
  }

  public void setStripeCustomerId(String stripeCustomerId) {
    this.stripeCustomerId = stripeCustomerId;
  }

  public String getStripeSubscriptionId() {
    return stripeSubscriptionId;
  }

  public void setStripeSubscriptionId(String stripeSubscriptionId) {
    this.stripeSubscriptionId = stripeSubscriptionId;
  }

  public PlanType getPlanType() {
    return planType;
  }

  public void setPlanType(PlanType planType) {
    this.planType = planType;
  }

  public SubscriptionStatus getStatus() {
    return status;
  }

  public void setStatus(SubscriptionStatus status) {
    this.status = status;
  }

  public OffsetDateTime getCurrentPeriodEnd() {
    return currentPeriodEnd;
  }

  public void setCurrentPeriodEnd(OffsetDateTime currentPeriodEnd) {
    this.currentPeriodEnd = currentPeriodEnd;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
