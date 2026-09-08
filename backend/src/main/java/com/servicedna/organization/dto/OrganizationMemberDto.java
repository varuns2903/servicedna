package com.servicedna.organization.dto;

import com.servicedna.organization.domain.OrganizationRole;
import java.util.UUID;

public record OrganizationMemberDto(UUID id, UUID userId, String email, OrganizationRole role) {}
