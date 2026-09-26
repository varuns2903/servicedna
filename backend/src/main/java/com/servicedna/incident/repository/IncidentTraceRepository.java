package com.servicedna.incident.repository;

import com.servicedna.incident.domain.IncidentTrace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentTraceRepository extends JpaRepository<IncidentTrace, UUID> {

  List<IncidentTrace> findByIncidentIdOrderByAttachedAt(UUID incidentId);

  Optional<IncidentTrace> findByIncidentIdAndTraceId(UUID incidentId, String traceId);
}
