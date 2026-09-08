package com.servicedna.alert.service;

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

    AlertRule rule =
        new AlertRule(
            UUID.randomUUID(),
            organization,
            service,
            request.condition(),
            request.webhookUrl(),
            request.integrationType());

    rule = alertRuleRepository.save(rule);
    return mapToDto(rule);
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

  private AlertRuleDto mapToDto(AlertRule rule) {
    return new AlertRuleDto(
        rule.getId(),
        rule.getOrganization().getId(),
        rule.getService().getId(),
        rule.getCondition(),
        rule.getWebhookUrl(),
        rule.getIntegrationType(),
        rule.getCreatedAt(),
        rule.getUpdatedAt());
  }
}
