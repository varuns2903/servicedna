package com.servicedna.billing.service;

import com.servicedna.billing.domain.PlanType;
import com.servicedna.billing.domain.Subscription;
import com.servicedna.billing.dto.PlanUsageDto;
import com.servicedna.billing.repository.SubscriptionRepository;
import com.servicedna.common.exception.ApiException;
import com.servicedna.service.repository.ServiceRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Enforces what each {@link PlanType} allows. */
@Service
public class PlanLimitService {

  private final SubscriptionRepository subscriptionRepository;
  private final ServiceRepository serviceRepository;
  private final boolean enforced;
  private final int defaultRetentionDays;

  public PlanLimitService(
      SubscriptionRepository subscriptionRepository,
      ServiceRepository serviceRepository,
      @Value("${billing.enforce-plan-limits:true}") boolean enforced,
      @Value("${PING_RETENTION_DAYS:90}") int defaultRetentionDays) {
    this.subscriptionRepository = subscriptionRepository;
    this.serviceRepository = serviceRepository;
    this.enforced = enforced;
    this.defaultRetentionDays = defaultRetentionDays;
  }

  public boolean isEnforced() {
    return enforced;
  }

  /**
   * Throws if the organization's plan doesn't allow another service. Runs inside the caller's
   * transaction and locks the subscription row, so concurrent creates can't both pass the check.
   * Organizations already over their limit (e.g. after a downgrade) keep their services but can't
   * add more.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void checkCanAddService(UUID organizationId) {
    PlanType plan = lockedPlan(organizationId);
    if (!allowsAnotherService(organizationId, plan)) {
      Integer max = plan.getMaxServices();
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "PLAN_LIMIT_REACHED",
          "The " + displayName(plan) + " plan allows up to " + max
              + " services. Upgrade your plan to add more.");
    }
  }

  /**
   * Like {@link #checkCanAddService} but returns false instead of throwing, for callers that skip
   * rather than fail (a transactional method that throws marks the caller's transaction
   * rollback-only even if the exception is caught).
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean canAddService(UUID organizationId) {
    return allowsAnotherService(organizationId, lockedPlan(organizationId));
  }

  private PlanType lockedPlan(UUID organizationId) {
    return subscriptionRepository
        .findForUpdateByOrganizationId(organizationId)
        .map(Subscription::getPlanType)
        .orElse(PlanType.FREE);
  }

  private boolean allowsAnotherService(UUID organizationId, PlanType plan) {
    if (!enforced) {
      return true;
    }
    Integer max = plan.getMaxServices();
    return max == null || serviceRepository.countByOrganizationId(organizationId) < max;
  }

  @Transactional(readOnly = true)
  public PlanUsageDto usage(UUID organizationId) {
    PlanType plan = planOf(organizationId);
    return new PlanUsageDto(
        plan,
        serviceRepository.countByOrganizationId(organizationId),
        enforced ? plan.getMaxServices() : null,
        retentionDays(plan),
        enforced);
  }

  /** Days of ping history kept for a plan: its own limit, never longer than PING_RETENTION_DAYS. */
  public int retentionDays(PlanType plan) {
    Integer days = enforced ? plan.getPingRetentionDays() : null;
    return days == null ? defaultRetentionDays : Math.min(days, defaultRetentionDays);
  }

  private PlanType planOf(UUID organizationId) {
    return subscriptionRepository
        .findByOrganizationId(organizationId)
        .map(Subscription::getPlanType)
        .orElse(PlanType.FREE);
  }

  private static String displayName(PlanType plan) {
    String name = plan.name();
    return name.charAt(0) + name.substring(1).toLowerCase();
  }
}
