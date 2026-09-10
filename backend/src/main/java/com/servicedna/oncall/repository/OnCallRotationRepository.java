package com.servicedna.oncall.repository;

import com.servicedna.oncall.domain.OnCallRotation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OnCallRotationRepository extends JpaRepository<OnCallRotation, UUID> {
  Optional<OnCallRotation> findByOrganizationId(UUID organizationId);
}
