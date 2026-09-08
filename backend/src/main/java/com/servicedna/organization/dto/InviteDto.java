package com.servicedna.organization.dto;

import com.servicedna.organization.domain.InviteStatus;
import com.servicedna.organization.domain.OrganizationRole;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InviteDto(
    UUID id,
    UUID organizationId,
    String email,
    OrganizationRole role,
    InviteStatus status,
    OffsetDateTime expiresAt,
    OffsetDateTime createdAt) {}
