package com.servicedna.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateMaintenanceWindowRequest(
    @NotNull(message = "Service ID is required") UUID serviceId,
    @NotBlank(message = "Title is required") String title,
    String description,
    @NotNull(message = "Start time is required") OffsetDateTime startTime,
    @NotNull(message = "End time is required") OffsetDateTime endTime) {}
