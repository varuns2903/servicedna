package com.servicedna.oncall.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.oncall.domain.OnCallRotation;
import com.servicedna.oncall.domain.OnCallRotationMember;
import com.servicedna.oncall.dto.OnCallMemberDto;
import com.servicedna.oncall.dto.OnCallRotationDto;
import com.servicedna.oncall.dto.UpsertOnCallRotationRequest;
import com.servicedna.oncall.repository.OnCallRotationMemberRepository;
import com.servicedna.oncall.repository.OnCallRotationRepository;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.repository.OrganizationMemberRepository;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.organization.service.OrganizationService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnCallService {

  private final OnCallRotationRepository onCallRotationRepository;
  private final OnCallRotationMemberRepository onCallRotationMemberRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationMemberRepository organizationMemberRepository;
  private final OrganizationService organizationService;

  public OnCallService(
      OnCallRotationRepository onCallRotationRepository,
      OnCallRotationMemberRepository onCallRotationMemberRepository,
      OrganizationRepository organizationRepository,
      OrganizationMemberRepository organizationMemberRepository,
      OrganizationService organizationService) {
    this.onCallRotationRepository = onCallRotationRepository;
    this.onCallRotationMemberRepository = onCallRotationMemberRepository;
    this.organizationRepository = organizationRepository;
    this.organizationMemberRepository = organizationMemberRepository;
    this.organizationService = organizationService;
  }

  @Transactional(readOnly = true)
  public OnCallRotationDto getRotation(UUID organizationId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    return onCallRotationRepository
        .findByOrganizationId(organizationId)
        .map(this::mapToDto)
        .orElse(new OnCallRotationDto(7, LocalDate.now(), List.of(), null, null));
  }

  @Transactional
  public OnCallRotationDto upsertRotation(
      UUID organizationId, UpsertOnCallRotationRequest request, UUID userId) {
    OrganizationMember requester = organizationService.validateUserAccess(organizationId, userId);
    if (requester.getRole() != OrganizationRole.OWNER && requester.getRole() != OrganizationRole.ADMIN) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Only owners and admins can edit the on-call rotation");
    }

    List<OrganizationMember> members =
        request.organizationMemberIds().stream()
            .map(
                memberId ->
                    organizationMemberRepository
                        .findById(memberId)
                        .filter(m -> m.getOrganization().getId().equals(organizationId))
                        .orElseThrow(
                            () ->
                                new ApiException(
                                    HttpStatus.BAD_REQUEST,
                                    "INVALID_MEMBER",
                                    "One or more selected members are not in this organization")))
            .toList();

    OnCallRotation rotation =
        onCallRotationRepository
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
                  return onCallRotationRepository.save(
                      new OnCallRotation(
                          UUID.randomUUID(), organization, request.rotationLengthDays(), request.startDate()));
                });

    rotation.setRotationLengthDays(request.rotationLengthDays());
    rotation.setStartDate(request.startDate());
    onCallRotationRepository.save(rotation);

    // Flush before re-inserting: Hibernate's default flush order runs inserts before deletes,
    // which would otherwise collide with the (rotation_id, position) unique constraint against
    // the not-yet-deleted old rows.
    onCallRotationMemberRepository.deleteByRotationId(rotation.getId());
    onCallRotationMemberRepository.flush();
    for (int i = 0; i < members.size(); i++) {
      onCallRotationMemberRepository.save(
          new OnCallRotationMember(UUID.randomUUID(), rotation, members.get(i), i));
    }

    return mapToDto(rotation);
  }

  private OnCallRotationDto mapToDto(OnCallRotation rotation) {
    List<OnCallRotationMember> rotationMembers =
        onCallRotationMemberRepository.findByRotationIdOrderByPositionAsc(rotation.getId());

    List<OnCallMemberDto> memberDtos =
        rotationMembers.stream()
            .map(
                rm ->
                    new OnCallMemberDto(
                        rm.getOrganizationMember().getId(),
                        rm.getOrganizationMember().getUser().getId(),
                        rm.getOrganizationMember().getUser().getEmail(),
                        rm.getPosition()))
            .toList();

    if (memberDtos.isEmpty()) {
      return new OnCallRotationDto(rotation.getRotationLengthDays(), rotation.getStartDate(), memberDtos, null, null);
    }

    long daysSinceStart =
        Math.max(0, ChronoUnit.DAYS.between(rotation.getStartDate(), LocalDate.now()));
    long periodIndex = daysSinceStart / rotation.getRotationLengthDays();
    int memberIndex = (int) (periodIndex % memberDtos.size());
    LocalDate shiftEndsOn =
        rotation.getStartDate().plusDays((periodIndex + 1) * rotation.getRotationLengthDays());

    return new OnCallRotationDto(
        rotation.getRotationLengthDays(),
        rotation.getStartDate(),
        memberDtos,
        memberDtos.get(memberIndex),
        shiftEndsOn);
  }
}
