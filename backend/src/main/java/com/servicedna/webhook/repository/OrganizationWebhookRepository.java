package com.servicedna.webhook.repository;

import com.servicedna.webhook.domain.OrganizationWebhook;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationWebhookRepository extends JpaRepository<OrganizationWebhook, UUID> {
  List<OrganizationWebhook> findByOrganizationId(UUID organizationId);

  Optional<OrganizationWebhook> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
