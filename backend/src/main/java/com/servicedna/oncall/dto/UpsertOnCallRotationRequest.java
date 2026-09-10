package com.servicedna.oncall.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UpsertOnCallRotationRequest(
    @Min(value = 1, message = "Rotation length must be at least 1 day") int rotationLengthDays,
    @NotNull(message = "Start date is required") LocalDate startDate,
    @NotEmpty(message = "At least one member is required") List<UUID> organizationMemberIds) {}
