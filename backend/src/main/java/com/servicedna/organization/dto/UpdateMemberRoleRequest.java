package com.servicedna.organization.dto;

import com.servicedna.organization.domain.OrganizationRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(@NotNull(message = "Role is required") OrganizationRole role) {}
