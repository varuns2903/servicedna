package com.servicedna.escalation.repository;

import com.servicedna.escalation.domain.EscalationPolicy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EscalationPolicyRepository extends JpaRepository<EscalationPolicy, UUID> {
  Optional<EscalationPolicy> findByOrganizationId(UUID organizationId);
}
