package com.servicedna.organization.repository;

import com.servicedna.organization.domain.OrganizationMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {
  List<OrganizationMember> findByOrganizationId(UUID organizationId);
  List<OrganizationMember> findByUserId(UUID userId);

  Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

  boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);
}
