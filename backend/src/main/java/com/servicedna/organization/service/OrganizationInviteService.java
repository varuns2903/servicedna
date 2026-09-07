package com.servicedna.organization.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.domain.InviteStatus;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.domain.OrganizationInvite;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.dto.CreateInviteRequest;
import com.servicedna.organization.dto.InviteDto;
import com.servicedna.organization.repository.OrganizationInviteRepository;
import com.servicedna.organization.repository.OrganizationMemberRepository;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.user.domain.User;
import com.servicedna.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrganizationInviteService {

    private final OrganizationInviteRepository inviteRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationService organizationService;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public OrganizationInviteService(
            OrganizationInviteRepository inviteRepository,
            OrganizationRepository organizationRepository,
            OrganizationService organizationService,
            OrganizationMemberRepository organizationMemberRepository,
            UserRepository userRepository
    ) {
        this.inviteRepository = inviteRepository;
        this.organizationRepository = organizationRepository;
        this.organizationService = organizationService;
        this.organizationMemberRepository = organizationMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public InviteDto createInvite(UUID organizationId, CreateInviteRequest request, UUID userId) {
        OrganizationMember inviterMember = organizationService.validateUserAccess(organizationId, userId);
        
        if (inviterMember.getRole() == OrganizationRole.VIEWER || inviterMember.getRole() == OrganizationRole.MEMBER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to invite members");
        }

        if (inviteRepository.existsByOrganizationIdAndEmailAndStatus(organizationId, request.email(), InviteStatus.PENDING)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVITE_EXISTS", "A pending invite for this email already exists");
        }

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORG_NOT_FOUND", "Organization not found"));

        OrganizationInvite invite = new OrganizationInvite(
                UUID.randomUUID(),
                organization,
                request.email(),
                request.role(),
                generateToken(),
                OffsetDateTime.now().plusDays(7)
        );

        invite = inviteRepository.save(invite);
        return mapToDto(invite);
    }

    @Transactional(readOnly = true)
    public List<InviteDto> getInvites(UUID organizationId, UUID userId) {
        organizationService.validateUserAccess(organizationId, userId);
        return inviteRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void acceptInvite(String token, UUID userId) {
        OrganizationInvite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND", "Invite not found or invalid"));

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INVITE", "Invite is no longer pending");
        }
        
        if (invite.getExpiresAt().isBefore(OffsetDateTime.now())) {
            invite.setStatus(InviteStatus.EXPIRED);
            inviteRepository.save(invite);
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVITE_EXPIRED", "Invite has expired");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        if (!user.getEmail().equalsIgnoreCase(invite.getEmail())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_MISMATCH", "This invite was sent to a different email address");
        }

        boolean alreadyMember = organizationMemberRepository.existsByOrganizationIdAndUserId(
                invite.getOrganization().getId(), userId);
                
        if (alreadyMember) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "You are already a member of this organization");
        }

        OrganizationMember newMember = new OrganizationMember(
                UUID.randomUUID(),
                invite.getOrganization(),
                user,
                invite.getRole()
        );
        organizationMemberRepository.save(newMember);

        invite.setStatus(InviteStatus.ACCEPTED);
        inviteRepository.save(invite);
    }

    private String generateToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private InviteDto mapToDto(OrganizationInvite invite) {
        return new InviteDto(
                invite.getId(),
                invite.getOrganization().getId(),
                invite.getEmail(),
                invite.getRole(),
                invite.getStatus(),
                invite.getExpiresAt(),
                invite.getCreatedAt()
        );
    }
}
