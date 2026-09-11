package com.servicedna.escalation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.servicedna.escalation.domain.EscalationPolicy;
import com.servicedna.escalation.repository.EscalationPolicyRepository;
import com.servicedna.incident.domain.Incident;
import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.incident.repository.IncidentRepository;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.user.domain.Role;
import com.servicedna.user.domain.User;
import com.servicedna.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class IncidentEscalationJobTest {

  @Autowired private IncidentEscalationJob incidentEscalationJob;
  @Autowired private EscalationPolicyRepository escalationPolicyRepository;
  @Autowired private IncidentRepository incidentRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager entityManager;

  @Test
  @Transactional
  void shouldEscalateOnlyUnacknowledgedIncidentsPastTheThreshold() {
    Organization org =
        organizationRepository.save(new Organization(UUID.randomUUID(), "Escalation Test Org"));
    User user =
        userRepository.save(
            new User(UUID.randomUUID(), "escalation-" + UUID.randomUUID() + "@example.com", "hash", Role.OWNER));
    escalationPolicyRepository.save(
        new EscalationPolicy(UUID.randomUUID(), org, "oncall-backup@example.com", 15));

    Incident overdue =
        incidentRepository.save(
            new Incident(
                UUID.randomUUID(), org, user, "Overdue incident", "desc", IncidentSeverity.CRITICAL));
    backdateCreatedAt(overdue.getId(), OffsetDateTime.now().minusMinutes(20));

    Incident tooRecent =
        incidentRepository.save(
            new Incident(
                UUID.randomUUID(), org, user, "Too recent", "desc", IncidentSeverity.CRITICAL));
    backdateCreatedAt(tooRecent.getId(), OffsetDateTime.now().minusMinutes(2));

    Incident alreadyAcked =
        incidentRepository.save(
            new Incident(
                UUID.randomUUID(), org, user, "Already acknowledged", "desc", IncidentSeverity.CRITICAL));
    entityManager.flush();
    // Both timestamps set via native SQL: a subsequent Hibernate save() on this same managed
    // entity would overwrite created_at with its stale in-memory value, undoing the backdate.
    entityManager
        .createNativeQuery(
            "update incidents set created_at = :createdAt, acknowledged_at = :ackAt where id = :id")
        .setParameter("createdAt", OffsetDateTime.now().minusMinutes(30))
        .setParameter("ackAt", OffsetDateTime.now().minusMinutes(25))
        .setParameter("id", alreadyAcked.getId())
        .executeUpdate();

    Incident lowSeverity =
        incidentRepository.save(
            new Incident(UUID.randomUUID(), org, user, "Low severity", "desc", IncidentSeverity.MINOR));
    backdateCreatedAt(lowSeverity.getId(), OffsetDateTime.now().minusMinutes(30));

    entityManager.flush();
    entityManager.clear();

    incidentEscalationJob.escalateUnacknowledgedIncidents();

    assertThat(incidentRepository.findById(overdue.getId()).orElseThrow().getEscalatedAt()).isNotNull();
    assertThat(incidentRepository.findById(tooRecent.getId()).orElseThrow().getEscalatedAt()).isNull();
    assertThat(incidentRepository.findById(alreadyAcked.getId()).orElseThrow().getEscalatedAt()).isNull();
    assertThat(incidentRepository.findById(lowSeverity.getId()).orElseThrow().getEscalatedAt()).isNull();
  }

  private void backdateCreatedAt(UUID incidentId, OffsetDateTime createdAt) {
    entityManager
        .createNativeQuery("update incidents set created_at = :createdAt where id = :id")
        .setParameter("createdAt", createdAt)
        .setParameter("id", incidentId)
        .executeUpdate();
  }
}
