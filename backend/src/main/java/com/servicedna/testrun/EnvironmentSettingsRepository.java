package com.servicedna.testrun;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentSettingsRepository extends JpaRepository<EnvironmentSettings, EnvironmentSettings.Key> {
  List<EnvironmentSettings> findByIdOrganizationId(UUID organizationId);
}
