package com.servicedna.auth.token;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiTokenRepository extends JpaRepository<ApiToken, UUID> {

  Optional<ApiToken> findByTokenHash(String tokenHash);

  List<ApiToken> findByUserIdOrderByCreatedAtDesc(UUID userId);

  Optional<ApiToken> findByIdAndUserId(UUID id, UUID userId);
}
