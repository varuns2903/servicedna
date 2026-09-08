package com.servicedna.service.repository;

import com.servicedna.service.domain.MaintenanceWindow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindow, UUID> {
  List<MaintenanceWindow> findByOrganizationIdOrderByStartTimeDesc(UUID organizationId);

  Optional<MaintenanceWindow> findByOrganizationIdAndId(UUID organizationId, UUID id);

  @Query(
      "SELECT COUNT(m) > 0 FROM MaintenanceWindow m WHERE m.service.id = :serviceId "
          + "AND m.status = 'IN_PROGRESS' "
          + "AND m.startTime <= :now AND m.endTime >= :now")
  boolean isServiceInActiveMaintenance(
      @Param("serviceId") UUID serviceId, @Param("now") OffsetDateTime now);
}
