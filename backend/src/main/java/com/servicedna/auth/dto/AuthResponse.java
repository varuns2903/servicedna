package com.servicedna.auth.dto;

public record AuthResponse(String token, String refreshToken, UserDto user) {}
