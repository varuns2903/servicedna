package com.servicedna.alert.dto;

import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.IntegrationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.URL;

public record CreateAlertRuleRequest(
    @NotNull(message = "Condition is required") AlertCondition condition,
    @URL(message = "Webhook URL must be valid") String webhookUrl,
    IntegrationType integrationType,
    IncidentSeverity incidentSeverity,
    @Positive(message = "Threshold must be positive") Double threshold,
    @Min(value = 1, message = "Window must be 1-60 minutes")
        @Max(value = 60, message = "Window must be 1-60 minutes")
        Integer windowMinutes) {}
