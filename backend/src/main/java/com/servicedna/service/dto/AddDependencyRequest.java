package com.servicedna.service.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddDependencyRequest(
    @NotNull(message = "Dependency service ID is required") UUID dependsOnServiceId) {}
