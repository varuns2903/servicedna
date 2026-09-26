package com.servicedna.alert.service;

import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.AlertRule;
import com.servicedna.alert.dto.AlertRuleDto;
import com.servicedna.alert.dto.CreateAlertRuleRequest;
import com.servicedna.alert.repository.AlertRuleRepository;
import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class AlertRuleService {

  private final AlertRuleRepository alertRuleRepository;
  private final OrganizationRepository organizationRepository;
  private final ServiceRepository serviceRepository;
  private final OrganizationService organizationService;

  public AlertRuleService(
      AlertRuleRepository alertRuleRepository,
      OrganizationRepository organizationRepository,
      ServiceRepository serviceRepository,
      OrganizationService organizationService) {
    this.alertRuleRepository = alertRuleRepository;
    this.organizationRepository = organizationRepository;
    this.serviceRepository = serviceRepository;
    this.organizationService = organizationService;
  }

  @Transactional
  public AlertRuleDto createAlertRule(
      UUID organizationId, UUID serviceId, CreateAlertRuleRequest request, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    Organization organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "ORG_NOT_FOUND", "Organization not found"));

    Service service =
        serviceRepository
            .findByOrganizationIdAndId(organizationId, serviceId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));

    AlertRule rule = alertRuleRepository.save(build(organization, service, request));
    return mapToDto(rule);
  }

  /**
   * Replaces the rules servicedna.yaml manages for a service (managed_by = CATALOG) with the ones
   * it now declares; rules made by hand are left alone.
   */
  @Transactional
  public List<AlertRuleDto> replaceCatalogRules(Service service, List<CreateAlertRuleRequest> requests) {
    List<AlertRule> rules = requests.stream().map(r -> build(service.getOrganization(), service, r)).toList();
    alertRuleRepository.deleteAll(alertRuleRepository.findByServiceIdAndManagedBy(service.getId(), CATALOG));
    alertRuleRepository.flush();
    rules.forEach(r -> r.setManagedBy(CATALOG));
    return alertRuleRepository.saveAll(rules).stream().map(this::mapToDto).toList();
  }

  public static final String CATALOG = "CATALOG";

  private AlertRule build(Organization organization, Service service, CreateAlertRuleRequest request) {
    boolean hasWebhook = request.webhookUrl() != null && !request.webhookUrl().isBlank();
    if (!hasWebhook && request.incidentSeverity() == null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "RULE_HAS_NO_ACTION",
          "An alert rule needs a webhook URL, an incident severity, or both.");
    }
    if (request.incidentSeverity() != null && request.condition() == AlertCondition.STATUS_RECOVERED) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_RULE",
          "Recovery can't open an incident; incidents opened by alerts resolve on recovery automatically.");
    }

    validateThreshold(request);

    return new AlertRule(
            UUID.randomUUID(),
            organization,
            service,
            request.condition(),
            hasWebhook ? request.webhookUrl() : null,
            request.integrationType(),
            request.incidentSeverity(),
            request.condition().isThreshold() ? request.threshold() : null,
            request.condition().usesWindow() ? request.windowMinutes() : null);
  }

  @Transactional(readOnly = true)
  public List<AlertRuleDto> getAlertRules(UUID organizationId, UUID serviceId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    return alertRuleRepository.findByOrganizationIdAndServiceId(organizationId, serviceId).stream()
        .map(this::mapToDto)
        .collect(Collectors.toList());
  }

  @Transactional
  public void deleteAlertRule(UUID organizationId, UUID ruleId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    AlertRule rule =
        alertRuleRepository
            .findByOrganizationIdAndId(organizationId, ruleId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Alert rule not found"));
    alertRuleRepository.delete(rule);
  }

  private static void validateThreshold(CreateAlertRuleRequest request) {
    AlertCondition condition = request.condition();
    if (!condition.isThreshold()) {
      return;
    }
    if (request.threshold() == null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_RULE", condition + " requires a threshold.");
    }
    if (condition == AlertCondition.ERROR_RATE_ABOVE && request.threshold() > 100) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_RULE", "Error rate threshold is a percentage (0-100).");
    }
    if (condition == AlertCondition.CONSECUTIVE_FAILURES
        && request.threshold() != Math.floor(request.threshold())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_RULE", "Consecutive failures must be a whole number.");
    }
    if (condition.usesWindow() && request.windowMinutes() == null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_RULE", condition + " requires windowMinutes.");
    }
  }

  private AlertRuleDto mapToDto(AlertRule rule) {
    return new AlertRuleDto(
        rule.getId(),
        rule.getOrganization().getId(),
        rule.getService().getId(),
        rule.getCondition(),
        rule.getWebhookUrl(),
        rule.getIntegrationType(),
        rule.getIncidentSeverity(),
        rule.getThreshold(),
        rule.getWindowMinutes(),
        rule.isBreached(),
        rule.getManagedBy(),
        rule.getCreatedAt(),
        rule.getUpdatedAt());
  }
}
