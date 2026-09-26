package com.servicedna.testrun;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TestRunRepository extends JpaRepository<TestRun, UUID> {

  Optional<TestRun> findByOrganizationIdAndId(UUID organizationId, UUID id);

  List<TestRun> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

  /** Queued runs a runner may take: its organizations (all, for a shared runner) and environment. */
  @Query(
      "select r.id from TestRun r where r.status = com.servicedna.testrun.TestRunStatus.QUEUED"
          + " and (:organizationId is null or r.organizationId = :organizationId)"
          + " and (r.environment is null or :environment is null or r.environment = :environment)"
          + " order by r.createdAt")
  List<UUID> findClaimable(UUID organizationId, String environment, Pageable pageable);

  /** Claims a run if it's still queued; returns 1 for the runner that won, 0 for any other. */
  @Modifying(clearAutomatically = true)
  @Query(
      "update TestRun r set r.status = com.servicedna.testrun.TestRunStatus.RUNNING, r.claimedAt = :now, r.runner = :runner"
          + " where r.id = :id and r.status = com.servicedna.testrun.TestRunStatus.QUEUED")
  int claim(UUID id, String runner, OffsetDateTime now);

  List<TestRun> findByStatusInAndCreatedAtBefore(Collection<TestRunStatus> statuses, OffsetDateTime before);

  List<TestRun> findByStatus(TestRunStatus status);
}
