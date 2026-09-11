package com.servicedna.webhook.service;

import com.servicedna.webhook.domain.OrganizationWebhook;
import com.servicedna.webhook.domain.WebhookType;
import com.servicedna.webhook.repository.OrganizationWebhookRepository;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Posts incident events to whatever Slack/Teams/generic webhooks an org has configured. Best
 * effort like MailService: a broken or unreachable webhook must never fail the incident
 * operation that triggered it, so every send is caught and logged rather than propagated.
 */
@Service
public class WebhookNotificationService {

  private static final Logger log = LoggerFactory.getLogger(WebhookNotificationService.class);

  private final OrganizationWebhookRepository webhookRepository;
  private final RestTemplate restTemplate;

  public WebhookNotificationService(
      OrganizationWebhookRepository webhookRepository, RestTemplateBuilder restTemplateBuilder) {
    this.webhookRepository = webhookRepository;
    this.restTemplate =
        restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(5))
            .build();
  }

  public void notify(UUID organizationId, String message, String link) {
    for (OrganizationWebhook webhook : webhookRepository.findByOrganizationId(organizationId)) {
      send(webhook, message, link);
    }
  }

  private void send(OrganizationWebhook webhook, String message, String link) {
    Object payload = buildPayload(webhook.getWebhookType(), message, link);
    try {
      restTemplate.postForEntity(webhook.getUrl(), payload, String.class);
    } catch (RestClientException e) {
      log.warn(
          "Could not deliver {} webhook for organization {}: {}",
          webhook.getWebhookType(),
          webhook.getOrganization().getId(),
          e.getMessage());
    }
  }

  private Object buildPayload(WebhookType type, String message, String link) {
    String fullText = message + "\n" + link;
    return switch (type) {
      case SLACK -> Map.of("text", fullText);
      case TEAMS ->
          Map.of(
              "@type", "MessageCard",
              "@context", "http://schema.org/extensions",
              "summary", message,
              "text", fullText);
      case GENERIC -> Map.of("message", message, "link", link);
    };
  }
}
