package com.servicedna.telemetry.repository;

import com.servicedna.billing.domain.PlanType;
import org.springframework.data.domain.Pageable;
import com.servicedna.telemetry.domain.ServicePing;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ServicePingRepository extends JpaRepository<ServicePing, UUID> {
  List<ServicePing> findByServiceIdOrderByCreatedAtDesc(UUID serviceId);

  List<ServicePing> findByServiceIdOrderByCreatedAtDesc(UUID serviceId, Pageable pageable);

  List<ServicePing> findByServiceIdAndCreatedAtAfterOrderByCreatedAtAsc(
      UUID serviceId, OffsetDateTime since);

  @Modifying(clearAutomatically = true)
  @Query("delete from ServicePing p where p.createdAt < :cutoff")
  int deleteByCreatedAtBefore(OffsetDateTime cutoff);

  /** Deletes pings older than the cutoff for services in organizations on the given plan. */
  @Modifying(clearAutomatically = true)
  @Query(
      "delete from ServicePing p where p.createdAt < :cutoff and p.service.id in"
          + " (select s.id from Service s, Subscription sub"
          + " where sub.organization = s.organization and sub.planType = :plan)")
  int deleteForPlanOlderThan(PlanType plan, OffsetDateTime cutoff);
}
