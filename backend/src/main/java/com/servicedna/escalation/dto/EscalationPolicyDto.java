package com.servicedna.escalation.dto;

public record EscalationPolicyDto(String escalationEmail, int escalateAfterMinutes) {}
