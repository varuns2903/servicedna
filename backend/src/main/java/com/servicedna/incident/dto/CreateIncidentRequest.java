package com.servicedna.incident.dto;

import com.servicedna.incident.domain.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateIncidentRequest(
    @NotBlank(message = "Title is required")
        @Size(min = 5, max = 255, message = "Title must be between 5 and 255 characters")
        String title,
    @NotBlank(message = "Description is required") String description,
    IncidentSeverity severity,
    List<UUID> affectedServiceIds) {}
