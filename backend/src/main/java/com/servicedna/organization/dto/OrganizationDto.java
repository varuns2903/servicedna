package com.servicedna.organization.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationDto(UUID id, String name, OffsetDateTime createdAt) {}
