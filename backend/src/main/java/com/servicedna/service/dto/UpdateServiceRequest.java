package com.servicedna.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record UpdateServiceRequest(
    @NotBlank(message = "Service name is required")
        @Size(min = 3, max = 255, message = "Service name must be between 3 and 255 characters")
        String name,
    String description,
    String repositoryUrl,
    String region,
    @URL(message = "Health check URL must be valid") String healthCheckUrl) {}
