package com.servicedna.incident.repository;

import com.servicedna.incident.domain.Incident;
import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.incident.domain.IncidentStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {
  List<Incident> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  Optional<Incident> findByOrganizationIdAndId(UUID organizationId, UUID id);

  List<Incident> findByOrganizationIdAndStatusNotAndAcknowledgedAtIsNullAndEscalatedAtIsNullAndSeverityInAndCreatedAtBefore(
      UUID organizationId, IncidentStatus status, List<IncidentSeverity> severities, OffsetDateTime cutoff);

  List<Incident> findByCreatedById(UUID userId);
}
