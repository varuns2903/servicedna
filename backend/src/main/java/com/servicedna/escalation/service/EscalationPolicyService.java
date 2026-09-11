package com.servicedna.escalation.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.escalation.domain.EscalationPolicy;
import com.servicedna.escalation.dto.EscalationPolicyDto;
import com.servicedna.escalation.dto.UpsertEscalationPolicyRequest;
import com.servicedna.escalation.repository.EscalationPolicyRepository;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.organization.service.OrganizationService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EscalationPolicyService {

  private static final EscalationPolicyDto DEFAULT = new EscalationPolicyDto(null, 15);

  private final EscalationPolicyRepository escalationPolicyRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationService organizationService;

  public EscalationPolicyService(
      EscalationPolicyRepository escalationPolicyRepository,
      OrganizationRepository organizationRepository,
      OrganizationService organizationService) {
    this.escalationPolicyRepository = escalationPolicyRepository;
    this.organizationRepository = organizationRepository;
    this.organizationService = organizationService;
  }

  @Transactional(readOnly = true)
  public EscalationPolicyDto getPolicy(UUID organizationId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    return escalationPolicyRepository
        .findByOrganizationId(organizationId)
        .map(this::mapToDto)
        .orElse(DEFAULT);
  }

  @Transactional
  public EscalationPolicyDto upsertPolicy(
      UUID organizationId, UpsertEscalationPolicyRequest request, UUID userId) {
    OrganizationMember requester = organizationService.validateUserAccess(organizationId, userId);
    if (requester.getRole() != OrganizationRole.OWNER && requester.getRole() != OrganizationRole.ADMIN) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Only owners and admins can edit the escalation policy");
    }

    EscalationPolicy policy =
        escalationPolicyRepository
            .findByOrganizationId(organizationId)
            .orElseGet(
                () -> {
                  Organization organization =
                      organizationRepository
                          .findById(organizationId)
                          .orElseThrow(
                              () ->
                                  new ApiException(
                                      HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND", "Organization not found"));
                  return new EscalationPolicy(
                      UUID.randomUUID(), organization, request.escalationEmail(), request.escalateAfterMinutes());
                });

    policy.setEscalationEmail(request.escalationEmail());
    policy.setEscalateAfterMinutes(request.escalateAfterMinutes());
    policy = escalationPolicyRepository.save(policy);

    return mapToDto(policy);
  }

  private EscalationPolicyDto mapToDto(EscalationPolicy policy) {
    return new EscalationPolicyDto(policy.getEscalationEmail(), policy.getEscalateAfterMinutes());
  }
}
