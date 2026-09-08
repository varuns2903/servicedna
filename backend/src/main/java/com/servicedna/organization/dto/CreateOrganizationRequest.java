package com.servicedna.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
    @NotBlank(message = "Organization name is required")
        @Size(
            min = 3,
            max = 255,
            message = "Organization name must be between 3 and 255 characters")
        String name) {}
