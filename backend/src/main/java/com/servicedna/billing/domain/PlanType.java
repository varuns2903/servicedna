package com.servicedna.billing.domain;

/** Subscription tiers and what each allows. {@code null} means unlimited. */
public enum PlanType {
  FREE(3, 1),
  PRO(50, 30),
  ENTERPRISE(null, null);

  private final Integer maxServices;
  private final Integer pingRetentionDays;

  PlanType(Integer maxServices, Integer pingRetentionDays) {
    this.maxServices = maxServices;
    this.pingRetentionDays = pingRetentionDays;
  }

  public Integer getMaxServices() {
    return maxServices;
  }

  /** Health-check history kept for this plan; unlimited plans fall back to PING_RETENTION_DAYS. */
  public Integer getPingRetentionDays() {
    return pingRetentionDays;
  }
}
