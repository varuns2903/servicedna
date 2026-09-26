package com.servicedna.ingestion.repository;

import com.servicedna.ingestion.domain.IngestionKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestionKeyRepository extends JpaRepository<IngestionKey, UUID> {
  List<IngestionKey> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  Optional<IngestionKey> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<IngestionKey> findByKeyHash(String keyHash);
}
