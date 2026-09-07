package com.servicedna.billing.dto;

import com.servicedna.billing.domain.PlanType;
import com.servicedna.billing.domain.SubscriptionStatus;

import java.time.OffsetDateTime;

public record SubscriptionDto(
    PlanType planType,
    SubscriptionStatus status,
    OffsetDateTime currentPeriodEnd
) {}
