package com.servicedna.telemetry.repository;

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

  List<ServicePing> findByServiceIdAndCreatedAtAfterOrderByCreatedAtAsc(
      UUID serviceId, OffsetDateTime since);

  @Modifying(clearAutomatically = true)
  @Query("delete from ServicePing p where p.createdAt < :cutoff")
  int deleteByCreatedAtBefore(OffsetDateTime cutoff);
}
