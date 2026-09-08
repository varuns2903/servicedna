package com.servicedna.auth.dto;

public record AuthResponse(String token, UserDto user) {}
