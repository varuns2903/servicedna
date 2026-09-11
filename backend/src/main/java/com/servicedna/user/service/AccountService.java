package com.servicedna.user.service;

import com.servicedna.auth.repository.EmailChangeTokenRepository;
import com.servicedna.auth.repository.EmailVerificationTokenRepository;
import com.servicedna.auth.repository.PasswordResetTokenRepository;
import com.servicedna.auth.repository.RefreshTokenRepository;
import com.servicedna.common.exception.ApiException;
import com.servicedna.incident.domain.Incident;
import com.servicedna.incident.repository.IncidentRepository;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.repository.OrganizationMemberRepository;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.user.domain.User;
import com.servicedna.user.dto.DataExportDto;
import com.servicedna.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

  private final UserRepository userRepository;
  private final OrganizationMemberRepository organizationMemberRepository;
  private final OrganizationService organizationService;
  private final IncidentRepository incidentRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final EmailVerificationTokenRepository emailVerificationTokenRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final EmailChangeTokenRepository emailChangeTokenRepository;
  private final PasswordEncoder passwordEncoder;

  public AccountService(
      UserRepository userRepository,
      OrganizationMemberRepository organizationMemberRepository,
      OrganizationService organizationService,
      IncidentRepository incidentRepository,
      RefreshTokenRepository refreshTokenRepository,
      EmailVerificationTokenRepository emailVerificationTokenRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      EmailChangeTokenRepository emailChangeTokenRepository,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.organizationMemberRepository = organizationMemberRepository;
    this.organizationService = organizationService;
    this.incidentRepository = incidentRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.emailVerificationTokenRepository = emailVerificationTokenRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.emailChangeTokenRepository = emailChangeTokenRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(readOnly = true)
  public DataExportDto exportUserData(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));

    List<DataExportDto.OrganizationMembershipExportDto> organizations =
        organizationMemberRepository.findByUserId(userId).stream()
            .map(
                (OrganizationMember m) ->
                    new DataExportDto.OrganizationMembershipExportDto(
                        m.getOrganization().getId(),
                        m.getOrganization().getName(),
                        m.getRole().name(),
                        m.getCreatedAt()))
            .toList();

    List<DataExportDto.IncidentExportDto> incidents =
        incidentRepository.findByCreatedById(userId).stream()
            .map(
                (Incident i) ->
                    new DataExportDto.IncidentExportDto(
                        i.getId(), i.getTitle(), i.getSeverity().name(), i.getStatus().name(), i.getCreatedAt()))
            .toList();

    return new DataExportDto(user.getId(), user.getEmail(), user.getCreatedAt(), organizations, incidents);
  }

  /**
   * A hard DELETE on the users row is not an option: incidents.created_by cascades on delete,
   * so removing a user would silently wipe every incident they ever reported. Instead this
   * leaves each org (reusing the same last-owner protection as removeMember — a sole owner must
   * transfer ownership or delete the org first), revokes all sessions/tokens, and anonymizes the
   * account in place so it can never log in again while incident/audit history stays intact.
   */
  @Transactional
  public void deleteAccount(UUID userId, String password) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Password is incorrect.");
    }

    List<OrganizationMember> memberships = organizationMemberRepository.findByUserId(userId);
    for (OrganizationMember membership : memberships) {
      // LAST_OWNER (from removeMember) surfaces here exactly as it would for a manual removal,
      // rolling back the whole deletion — the user must transfer ownership or delete that org
      // before their account can be deleted.
      organizationService.removeMember(membership.getOrganization().getId(), membership.getId(), userId);
    }

    refreshTokenRepository.deleteByUserId(userId);
    emailVerificationTokenRepository.findByUserId(userId).ifPresent(emailVerificationTokenRepository::delete);
    passwordResetTokenRepository.findByUserId(userId).ifPresent(passwordResetTokenRepository::delete);
    emailChangeTokenRepository.findByUserId(userId).ifPresent(emailChangeTokenRepository::delete);

    user.setEmail("deleted-" + UUID.randomUUID() + "@deleted.local");
    user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
    user.setDeletedAt(OffsetDateTime.now());
    userRepository.save(user);
  }
}
