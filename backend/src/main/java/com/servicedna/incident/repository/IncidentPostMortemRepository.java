package com.servicedna.incident.repository;

import com.servicedna.incident.domain.IncidentPostMortem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentPostMortemRepository extends JpaRepository<IncidentPostMortem, UUID> {
  Optional<IncidentPostMortem> findByIncidentId(UUID incidentId);
}
