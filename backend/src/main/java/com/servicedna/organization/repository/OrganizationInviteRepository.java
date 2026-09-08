package com.servicedna.organization.repository;

import com.servicedna.organization.domain.InviteStatus;
import com.servicedna.organization.domain.OrganizationInvite;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationInviteRepository extends JpaRepository<OrganizationInvite, UUID> {
  List<OrganizationInvite> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  Optional<OrganizationInvite> findByToken(String token);

  boolean existsByOrganizationIdAndEmailAndStatus(
      UUID organizationId, String email, InviteStatus status);
}
