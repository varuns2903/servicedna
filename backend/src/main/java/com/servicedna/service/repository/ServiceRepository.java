package com.servicedna.service.repository;

import com.servicedna.service.domain.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceRepository extends JpaRepository<Service, UUID> {
  List<Service> findByOrganizationId(UUID organizationId);

  Optional<Service> findByOrganizationIdAndId(UUID organizationId, UUID id);

  boolean existsByOrganizationIdAndName(UUID organizationId, String name);

  Optional<Service> findByApiKey(String apiKey);
}
