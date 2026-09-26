package com.servicedna.testrun;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestSuiteRepository extends JpaRepository<TestSuite, UUID> {
  Optional<TestSuite> findByOrganizationIdAndId(UUID organizationId, UUID id);

  List<TestSuite> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

  List<TestSuite> findByStatus(String status);
}
