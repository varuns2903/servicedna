package com.servicedna.auth.token;

import com.servicedna.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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
 * Personal API tokens for the CLI and CI (`SDNA_TOKEN`), and for SSO users who have no password to
 * log in with. A token acts as its user, with the user's organization roles.
 */
@Service
public class ApiTokenService {

  public static final String PREFIX = "sdna_pat_";
  private static final int MAX_TOKENS = 25;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final ApiTokenRepository repository;

  public ApiTokenService(ApiTokenRepository repository) {
    this.repository = repository;
  }

  public record Token(UUID id, String name, String prefix, OffsetDateTime createdAt, OffsetDateTime expiresAt, OffsetDateTime lastUsedAt,
      /** Only in the response that creates it. */
      String token) {}

  public record CreateRequest(String name, Integer expiresInDays) {}

  @Transactional
  public Token create(UUID userId, CreateRequest request) {
    String name = request.name() == null ? "" : request.name().trim();
    if (name.isEmpty() || name.length() > 100) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN_NAME", "Name the token (at most 100 characters), e.g. \"CI\".");
    }
    if (request.expiresInDays() != null && (request.expiresInDays() < 1 || request.expiresInDays() > 366)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXPIRY", "Tokens expire in 1 to 366 days, or never.");
    }
    if (repository.findByUserIdOrderByCreatedAtDesc(userId).size() >= MAX_TOKENS) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_TOKENS", "At most " + MAX_TOKENS + " tokens; revoke one first.");
    }
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String raw = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    OffsetDateTime expires = request.expiresInDays() == null ? null : OffsetDateTime.now().plusDays(request.expiresInDays());
    ApiToken token = repository.save(new ApiToken(userId, name, hash(raw), raw.substring(0, PREFIX.length() + 4), expires));
    return new Token(token.getId(), token.getName(), token.getTokenPrefix(), token.getCreatedAt(), token.getExpiresAt(), null, raw);
  }

  @Transactional(readOnly = true)
  public List<Token> list(UUID userId) {
    return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(t -> new Token(t.getId(), t.getName(), t.getTokenPrefix(), t.getCreatedAt(), t.getExpiresAt(), t.getLastUsedAt(), null))
        .toList();
  }

  @Transactional
  public void revoke(UUID userId, UUID tokenId) {
    repository.delete(repository.findByIdAndUserId(tokenId, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOKEN_NOT_FOUND", "Token not found")));
  }

  /** The user a live token belongs to; records when it was last used (at most once a minute). */
  @Transactional
  public Optional<UUID> authenticate(String raw) {
    if (raw == null || !raw.startsWith(PREFIX)) {
      return Optional.empty();
    }
    return repository.findByTokenHash(hash(raw)).filter(t -> !t.isExpired()).map(t -> {
      if (t.getLastUsedAt() == null || t.getLastUsedAt().isBefore(OffsetDateTime.now().minusMinutes(1))) {
        t.setLastUsedAt(OffsetDateTime.now());
      }
      return t.getUserId();
    });
  }

  static String hash(String raw) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
