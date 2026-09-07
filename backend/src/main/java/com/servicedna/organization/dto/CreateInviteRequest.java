package com.servicedna.organization.dto;

import com.servicedna.organization.domain.OrganizationRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInviteRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email format")
    String email,

    @NotNull(message = "Role is required")
    OrganizationRole role
) {}
