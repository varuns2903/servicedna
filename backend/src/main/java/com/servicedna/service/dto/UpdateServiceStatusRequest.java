package com.servicedna.service.dto;

import com.servicedna.service.domain.ServiceStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateServiceStatusRequest(
    @NotNull(message = "Status is required") ServiceStatus status) {}
