package com.servicedna.alert.dto;

import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.IntegrationType;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;

public record CreateAlertRuleRequest(
    @NotNull(message = "Condition is required") AlertCondition condition,
    @URL(message = "Webhook URL must be valid") String webhookUrl,
    IntegrationType integrationType,
    IncidentSeverity incidentSeverity) {}
