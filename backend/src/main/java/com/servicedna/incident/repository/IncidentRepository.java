package com.servicedna.incident.repository;

import com.servicedna.incident.domain.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    List<Incident> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    Optional<Incident> findByOrganizationIdAndId(UUID organizationId, UUID id);
}
