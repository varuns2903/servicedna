package com.servicedna.alert.dto;

import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.IntegrationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;

public record CreateAlertRuleRequest(
    @NotNull(message = "Condition is required") AlertCondition condition,
    @NotBlank(message = "Webhook URL is required") @URL(message = "Webhook URL must be valid")
        String webhookUrl,
    IntegrationType integrationType) {}
