package com.servicedna.incident.repository;

import com.servicedna.incident.domain.IncidentEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentEventRepository extends JpaRepository<IncidentEvent, UUID> {
  List<IncidentEvent> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}
