package com.servicedna.webhook.controller;

import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.webhook.dto.CreateWebhookRequest;
import com.servicedna.webhook.dto.WebhookDto;
import com.servicedna.webhook.service.WebhookService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/webhooks")
public class WebhookController {

  private final WebhookService webhookService;

  public WebhookController(WebhookService webhookService) {
    this.webhookService = webhookService;
  }

  @GetMapping
  public ResponseEntity<List<WebhookDto>> getWebhooks(
      @PathVariable UUID orgId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(webhookService.getWebhooks(orgId, userDetails.getUser().getId()));
  }

  @PostMapping
  public ResponseEntity<WebhookDto> createWebhook(
      @PathVariable UUID orgId,
      @Valid @RequestBody CreateWebhookRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        webhookService.createWebhook(orgId, request, userDetails.getUser().getId()));
  }

  @DeleteMapping("/{webhookId}")
  public ResponseEntity<Void> deleteWebhook(
      @PathVariable UUID orgId,
      @PathVariable UUID webhookId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    webhookService.deleteWebhook(orgId, webhookId, userDetails.getUser().getId());
    return ResponseEntity.ok().build();
  }
}
