package com.servicedna.billing.dto;

import com.servicedna.billing.domain.PlanType;

/**
 * An organization's plan and how much of it is used. {@code maxServices} is null when unlimited;
 * when {@code limitsEnforced} is false (self-hosted deployments can turn enforcement off) no limit
 * applies regardless of plan.
 */
public record PlanUsageDto(
    PlanType planType,
    long serviceCount,
    Integer maxServices,
    int pingRetentionDays,
    boolean limitsEnforced) {}
