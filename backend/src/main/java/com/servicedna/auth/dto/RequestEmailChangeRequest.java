package com.servicedna.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestEmailChangeRequest(
    @NotBlank(message = "Password is required") String password,
    @NotBlank(message = "Email is required") @Email(message = "Invalid email format")
        String newEmail) {}
