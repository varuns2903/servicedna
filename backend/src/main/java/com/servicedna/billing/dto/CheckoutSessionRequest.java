package com.servicedna.billing.dto;

import com.servicedna.billing.domain.PlanType;
import jakarta.validation.constraints.NotNull;

public record CheckoutSessionRequest(
    @NotNull(message = "Plan type is required")
    PlanType planType
) {}
