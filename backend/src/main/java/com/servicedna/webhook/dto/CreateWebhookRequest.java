package com.servicedna.webhook.dto;

import com.servicedna.webhook.domain.WebhookType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateWebhookRequest(
    @NotBlank(message = "URL is required")
        @Pattern(regexp = "^https://.+", message = "Webhook URL must use https://")
        String url,
    @NotNull(message = "Webhook type is required") WebhookType webhookType) {}
