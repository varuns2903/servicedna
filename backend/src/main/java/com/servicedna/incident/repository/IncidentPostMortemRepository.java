package com.servicedna.incident.repository;

import com.servicedna.incident.domain.IncidentPostMortem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IncidentPostMortemRepository extends JpaRepository<IncidentPostMortem, UUID> {
    Optional<IncidentPostMortem> findByIncidentId(UUID incidentId);
}
