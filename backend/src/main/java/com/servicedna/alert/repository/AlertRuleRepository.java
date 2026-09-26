package com.servicedna.alert.repository;

import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.AlertRule;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {
  List<AlertRule> findByOrganizationIdAndServiceId(UUID organizationId, UUID serviceId);

  List<AlertRule> findByServiceIdAndCondition(UUID serviceId, AlertCondition condition);

  Optional<AlertRule> findByOrganizationIdAndId(UUID organizationId, UUID id);

  List<AlertRule> findByServiceIdAndManagedBy(UUID serviceId, String managedBy);

  boolean existsByServiceIdAndBreachedTrue(UUID serviceId);

  /** Rules with their service and organization loaded, for evaluation outside a transaction. */
  @Query(
      "select r from AlertRule r join fetch r.service join fetch r.organization"
          + " where r.condition in :conditions")
  List<AlertRule> findWithServiceByConditionIn(Collection<AlertCondition> conditions);
}
