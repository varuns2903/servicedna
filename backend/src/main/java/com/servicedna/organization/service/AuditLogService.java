package com.servicedna.organization.service;

import com.servicedna.organization.domain.AuditLog;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.dto.AuditLogDto;
import com.servicedna.organization.repository.AuditLogRepository;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.user.domain.User;
import com.servicedna.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

  private final AuditLogRepository auditLogRepository;
  private final OrganizationRepository organizationRepository;
  private final UserRepository userRepository;
  private final OrganizationService organizationService;

  public AuditLogService(
      AuditLogRepository auditLogRepository,
      OrganizationRepository organizationRepository,
      UserRepository userRepository,
      OrganizationService organizationService) {
    this.auditLogRepository = auditLogRepository;
    this.organizationRepository = organizationRepository;
    this.userRepository = userRepository;
    this.organizationService = organizationService;
  }

  @Transactional(readOnly = true)
  public Page<AuditLogDto> getAuditLogs(UUID organizationId, UUID userId, Pageable pageable) {
    organizationService.validateUserAccess(organizationId, userId);

    return auditLogRepository.findByOrganizationId(organizationId, pageable).map(this::mapToDto);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void logAction(
      UUID organizationId,
      UUID userId,
      String action,
      String entityType,
      String entityId,
      String details,
      String ipAddress) {
    Organization org = organizationRepository.findById(organizationId).orElse(null);
    if (org == null) return;

    User user = null;
    if (userId != null) {
      user = userRepository.findById(userId).orElse(null);
    }

    AuditLog log =
        new AuditLog(
            UUID.randomUUID(), org, user, action, entityType, entityId, details, ipAddress);

    auditLogRepository.save(log);
  }

  private AuditLogDto mapToDto(AuditLog log) {
    return new AuditLogDto(
        log.getId(),
        log.getOrganization().getId(),
        log.getUser() != null ? log.getUser().getId() : null,
        log.getUser() != null ? log.getUser().getEmail() : null,
        log.getAction(),
        log.getEntityType(),
        log.getEntityId(),
        log.getDetails(),
        log.getIpAddress(),
        log.getCreatedAt());
  }
}
