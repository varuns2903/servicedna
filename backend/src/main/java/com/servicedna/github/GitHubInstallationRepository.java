package com.servicedna.github;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubInstallationRepository extends JpaRepository<GitHubInstallation, Long> {

  List<GitHubInstallation> findByOrganizationIdOrderByInstalledAt(UUID organizationId);
}
