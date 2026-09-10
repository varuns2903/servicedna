package com.servicedna.organization.service;

import com.servicedna.billing.domain.Subscription;
import com.servicedna.billing.repository.SubscriptionRepository;
import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.dto.AddMemberRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.organization.dto.OrganizationDto;
import com.servicedna.organization.dto.OrganizationMemberDto;
import com.servicedna.organization.repository.OrganizationMemberRepository;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.user.domain.User;
import com.servicedna.user.repository.UserRepository;
import java.util.List;
import com.servicedna.organization.dto.UpdateOrganizationRequest;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

  private final OrganizationRepository organizationRepository;
  private final OrganizationMemberRepository organizationMemberRepository;
  private final UserRepository userRepository;
  private final SubscriptionRepository subscriptionRepository;

  public OrganizationService(
      OrganizationRepository organizationRepository,
      OrganizationMemberRepository organizationMemberRepository,
      UserRepository userRepository,
      SubscriptionRepository subscriptionRepository) {
    this.organizationRepository = organizationRepository;
    this.organizationMemberRepository = organizationMemberRepository;
    this.userRepository = userRepository;
    this.subscriptionRepository = subscriptionRepository;
  }

  @Transactional
  public OrganizationDto createOrganization(CreateOrganizationRequest request, UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

    Organization organization = new Organization(UUID.randomUUID(), request.name());
    organization = organizationRepository.saveAndFlush(organization);

    OrganizationMember member =
        new OrganizationMember(UUID.randomUUID(), organization, user, OrganizationRole.OWNER);
    organizationMemberRepository.save(member);

    Subscription subscription = new Subscription(UUID.randomUUID(), organization);
    subscriptionRepository.save(subscription);

    return mapToDto(organization);
  }

  @Transactional(readOnly = true)
  public List<OrganizationDto> getUserOrganizations(UUID userId) {
    return organizationMemberRepository.findByUserId(userId).stream()
        .map(member -> mapToDto(member.getOrganization()))
        .collect(Collectors.toList());
  }

  @Transactional
  public OrganizationMemberDto addMember(
      UUID organizationId, AddMemberRequest request, UUID requesterId) {
    OrganizationMember requester =
        organizationMemberRepository
            .findByOrganizationIdAndUserId(organizationId, requesterId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.FORBIDDEN,
                        "ACCESS_DENIED",
                        "You are not a member of this organization"));

    if (requester.getRole() != OrganizationRole.OWNER
        && requester.getRole() != OrganizationRole.ADMIN) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Only owners and admins can add members");
    }

    User userToAdd =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "User with email " + request.email() + " not found"));

    if (organizationMemberRepository.existsByOrganizationIdAndUserId(
        organizationId, userToAdd.getId())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "USER_ALREADY_MEMBER",
          "User is already a member of this organization");
    }

    Organization organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "ORG_NOT_FOUND", "Organization not found"));

    OrganizationMember newMember =
        new OrganizationMember(UUID.randomUUID(), organization, userToAdd, request.role());

    organizationMemberRepository.save(newMember);

    return new OrganizationMemberDto(
        newMember.getId(), userToAdd.getId(), userToAdd.getEmail(), newMember.getRole());
  }

  public OrganizationMember validateUserAccess(UUID organizationId, UUID userId) {
    return organizationMemberRepository
        .findByOrganizationIdAndUserId(organizationId, userId)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "You do not have access to this organization"));
  }

  private OrganizationDto mapToDto(Organization organization) {
    return new OrganizationDto(
        organization.getId(), organization.getName(), organization.getCreatedAt());
  }
  public OrganizationDto updateOrganization(UUID orgId, UpdateOrganizationRequest request, UUID userId) {
    validateUserAccess(orgId, userId);
    Organization org = organizationRepository.findById(orgId).orElseThrow();
    org.setName(request.name());
    return mapToDto(organizationRepository.save(org));
  }

  public List<OrganizationMemberDto> getMembers(UUID orgId, UUID userId) {
    validateUserAccess(orgId, userId);
    return organizationMemberRepository.findByOrganizationId(orgId).stream()
        .map(m -> new OrganizationMemberDto(m.getId(), m.getUser().getId(), m.getUser().getEmail(), m.getRole()))
        .collect(java.util.stream.Collectors.toList());
  }

  @Transactional
  public OrganizationMemberDto updateMemberRole(
      UUID organizationId, UUID memberId, OrganizationRole newRole, UUID requesterId) {
    OrganizationMember requester = validateUserAccess(organizationId, requesterId);
    if (requester.getRole() != OrganizationRole.OWNER
        && requester.getRole() != OrganizationRole.ADMIN) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Only owners and admins can change member roles");
    }

    OrganizationMember target = findMemberInOrg(organizationId, memberId);

    boolean touchesOwnerTier = target.getRole() == OrganizationRole.OWNER || newRole == OrganizationRole.OWNER;
    if (touchesOwnerTier && requester.getRole() != OrganizationRole.OWNER) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Only an owner can change an owner's role");
    }

    if (target.getRole() == OrganizationRole.OWNER
        && newRole != OrganizationRole.OWNER
        && countOwners(organizationId) <= 1) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "LAST_OWNER",
          "An organization must have at least one owner — promote another member first");
    }

    target.setRole(newRole);
    organizationMemberRepository.save(target);

    return new OrganizationMemberDto(
        target.getId(), target.getUser().getId(), target.getUser().getEmail(), target.getRole());
  }

  @Transactional
  public void removeMember(UUID organizationId, UUID memberId, UUID requesterId) {
    OrganizationMember requester = validateUserAccess(organizationId, requesterId);
    OrganizationMember target = findMemberInOrg(organizationId, memberId);

    boolean removingSelf = target.getUser().getId().equals(requesterId);
    if (!removingSelf
        && requester.getRole() != OrganizationRole.OWNER
        && requester.getRole() != OrganizationRole.ADMIN) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Only owners and admins can remove members");
    }

    if (target.getRole() == OrganizationRole.OWNER && countOwners(organizationId) <= 1) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "LAST_OWNER",
          "An organization must have at least one owner — promote another member before removing this one");
    }

    organizationMemberRepository.delete(target);
  }

  private OrganizationMember findMemberInOrg(UUID organizationId, UUID memberId) {
    OrganizationMember member =
        organizationMemberRepository
            .findById(memberId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Member not found"));
    if (!member.getOrganization().getId().equals(organizationId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Member not found");
    }
    return member;
  }

  private long countOwners(UUID organizationId) {
    return organizationMemberRepository.findByOrganizationId(organizationId).stream()
        .filter(m -> m.getRole() == OrganizationRole.OWNER)
        .count();
  }
}
