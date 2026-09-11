package com.servicedna.webhook.dto;

import com.servicedna.webhook.domain.WebhookType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WebhookDto(UUID id, String url, WebhookType webhookType, OffsetDateTime createdAt) {}
