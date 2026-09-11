package com.servicedna.escalation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpsertEscalationPolicyRequest(
    @NotBlank(message = "Escalation email is required")
        @Email(message = "Invalid email format")
        String escalationEmail,
    @Min(value = 1, message = "Escalation delay must be at least 1 minute") int escalateAfterMinutes) {}
