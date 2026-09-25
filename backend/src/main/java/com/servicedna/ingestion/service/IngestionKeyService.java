package com.servicedna.ingestion.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.ingestion.domain.IngestionKey;
import com.servicedna.ingestion.dto.IngestionKeyDto;
import com.servicedna.ingestion.repository.IngestionKeyRepository;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.service.AuditLogService;
import com.servicedna.organization.service.OrganizationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organization-wide telemetry keys: one key lets every SDK, collector and agent in an org send
 * data, with the service identified by its own telemetry rather than by a per-service key.
 */
@Service
public class IngestionKeyService {

  public static final String KEY_PREFIX = "sdna_";
  private static final int DISPLAY_PREFIX_LENGTH = 12;
  /** last_used_at is informational; writing it on every request would be a write per batch. */
  private static final Duration LAST_USED_RESOLUTION = Duration.ofMinutes(5);

  private final IngestionKeyRepository repository;
  private final OrganizationService organizationService;
  private final AuditLogService auditLogService;
  private final SecureRandom random = new SecureRandom();

  public IngestionKeyService(
      IngestionKeyRepository repository,
      OrganizationService organizationService,
      AuditLogService auditLogService) {
    this.repository = repository;
    this.organizationService = organizationService;
    this.auditLogService = auditLogService;
  }

  @Transactional(readOnly = true)
  public List<IngestionKeyDto> list(UUID organizationId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    return repository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
        .map(key -> toDto(key, null))
        .toList();
  }

  @Transactional
  public IngestionKeyDto create(UUID organizationId, String name, UUID userId) {
    OrganizationMember member = requireAdmin(organizationId, userId);
    byte[] secret = new byte[32];
    random.nextBytes(secret);
    String raw = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);

    Organization organization = member.getOrganization();
    IngestionKey key =
        repository.save(
            new IngestionKey(
                UUID.randomUUID(),
                organization,
                name.trim(),
                hash(raw),
                raw.substring(0, DISPLAY_PREFIX_LENGTH),
                member.getUser()));
    auditLogService.logAction(
        organizationId, userId, "INGESTION_KEY_CREATED", "IngestionKey", key.getId().toString(), key.getName(), null);
    return toDto(key, raw);
  }

  @Transactional
  public IngestionKeyDto revoke(UUID organizationId, UUID keyId, UUID userId) {
    requireAdmin(organizationId, userId);
    IngestionKey key =
        repository
            .findByIdAndOrganizationId(keyId, organizationId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "INGESTION_KEY_NOT_FOUND", "Ingestion key not found"));
    if (!key.isRevoked()) {
      key.setRevokedAt(OffsetDateTime.now());
      auditLogService.logAction(
          organizationId, userId, "INGESTION_KEY_REVOKED", "IngestionKey", key.getId().toString(), key.getName(), null);
    }
    return toDto(key, null);
  }

  /** The organization a raw key belongs to, if the key exists and isn't revoked. */
  @Transactional
  public Optional<UUID> authenticate(String rawKey) {
    if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
      return Optional.empty();
    }
    return repository
        .findByKeyHash(hash(rawKey))
        .filter(key -> !key.isRevoked())
        .map(
            key -> {
              OffsetDateTime now = OffsetDateTime.now();
              if (key.getLastUsedAt() == null
                  || key.getLastUsedAt().isBefore(now.minus(LAST_USED_RESOLUTION))) {
                key.setLastUsedAt(now);
              }
              return key.getOrganization().getId();
            });
  }

  private OrganizationMember requireAdmin(UUID organizationId, UUID userId) {
    OrganizationMember member = organizationService.validateUserAccess(organizationId, userId);
    if (member.getRole() != OrganizationRole.OWNER && member.getRole() != OrganizationRole.ADMIN) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Only owners and admins can manage ingestion keys");
    }
    return member;
  }

  static String hash(String raw) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static IngestionKeyDto toDto(IngestionKey key, String raw) {
    return new IngestionKeyDto(
        key.getId(),
        key.getName(),
        key.getKeyPrefix(),
        raw,
        key.getCreatedBy() != null ? key.getCreatedBy().getEmail() : null,
        key.getCreatedAt(),
        key.getLastUsedAt(),
        key.getRevokedAt());
  }
}
