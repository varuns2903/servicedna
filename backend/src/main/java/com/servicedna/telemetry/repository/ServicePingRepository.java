package com.servicedna.telemetry.repository;

import com.servicedna.telemetry.domain.ServicePing;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServicePingRepository extends JpaRepository<ServicePing, UUID> {
  List<ServicePing> findByServiceIdOrderByCreatedAtDesc(UUID serviceId);
}
