package com.servicedna.auth.dto;

import java.util.UUID;

public record UserDto(UUID id, String email, String role) {}
