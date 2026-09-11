package com.servicedna.webhook.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.webhook.domain.OrganizationWebhook;
import com.servicedna.webhook.dto.CreateWebhookRequest;
import com.servicedna.webhook.dto.WebhookDto;
import com.servicedna.webhook.repository.OrganizationWebhookRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {

  private final OrganizationWebhookRepository webhookRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationService organizationService;

  public WebhookService(
      OrganizationWebhookRepository webhookRepository,
      OrganizationRepository organizationRepository,
      OrganizationService organizationService) {
    this.webhookRepository = webhookRepository;
    this.organizationRepository = organizationRepository;
    this.organizationService = organizationService;
  }

  @Transactional(readOnly = true)
  public List<WebhookDto> getWebhooks(UUID organizationId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    return webhookRepository.findByOrganizationId(organizationId).stream()
        .map(this::mapToDto)
        .collect(Collectors.toList());
  }

  @Transactional
  public WebhookDto createWebhook(UUID organizationId, CreateWebhookRequest request, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    Organization organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "ORG_NOT_FOUND", "Organization not found"));

    OrganizationWebhook webhook =
        new OrganizationWebhook(
            UUID.randomUUID(), organization, request.url(), request.webhookType());
    return mapToDto(webhookRepository.saveAndFlush(webhook));
  }

  @Transactional
  public void deleteWebhook(UUID organizationId, UUID webhookId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    OrganizationWebhook webhook =
        webhookRepository
            .findByIdAndOrganizationId(webhookId, organizationId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "WEBHOOK_NOT_FOUND", "Webhook not found"));
    webhookRepository.delete(webhook);
  }

  private WebhookDto mapToDto(OrganizationWebhook webhook) {
    return new WebhookDto(
        webhook.getId(), webhook.getUrl(), webhook.getWebhookType(), webhook.getCreatedAt());
  }
}
