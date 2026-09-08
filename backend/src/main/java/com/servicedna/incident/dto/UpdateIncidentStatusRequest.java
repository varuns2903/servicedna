package com.servicedna.incident.dto;

import com.servicedna.incident.domain.IncidentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateIncidentStatusRequest(
    @NotNull(message = "Status is required") IncidentStatus status) {}
