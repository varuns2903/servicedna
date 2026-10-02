package com.servicedna.github;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GitHubCheckRunRepository extends JpaRepository<GitHubCheckRun, UUID> {

  @Query("select c from GitHubCheckRun c where c.completed = false and c.suiteIds like concat('%', :suiteId, '%')")
  List<GitHubCheckRun> findOpenWaitingFor(String suiteId);

  /** Marks it completed unless someone already did; 1 means the caller reports it. */
  @Modifying
  @Query("update GitHubCheckRun c set c.completed = true where c.id = :id and c.completed = false")
  int markCompleted(UUID id);
}
