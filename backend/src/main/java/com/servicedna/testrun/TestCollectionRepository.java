package com.servicedna.testrun;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCollectionRepository extends JpaRepository<TestCollection, UUID> {
  List<TestCollection> findByOrganizationIdOrderByName(UUID organizationId);

  Optional<TestCollection> findByOrganizationIdAndId(UUID organizationId, UUID id);

  Optional<TestCollection> findByOrganizationIdAndName(UUID organizationId, String name);
}
