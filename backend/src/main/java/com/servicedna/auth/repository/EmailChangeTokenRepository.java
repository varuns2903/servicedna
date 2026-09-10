package com.servicedna.auth.repository;

import com.servicedna.auth.domain.EmailChangeToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailChangeTokenRepository extends JpaRepository<EmailChangeToken, UUID> {
  Optional<EmailChangeToken> findByToken(String token);

  Optional<EmailChangeToken> findByUserId(UUID userId);
}
