package com.servicedna.oncall.dto;

import java.util.UUID;

public record OnCallMemberDto(
    UUID organizationMemberId, UUID userId, String email, int position) {}
