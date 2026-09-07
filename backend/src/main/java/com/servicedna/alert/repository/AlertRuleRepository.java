package com.servicedna.alert.repository;

import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {
    List<AlertRule> findByOrganizationIdAndServiceId(UUID organizationId, UUID serviceId);
    List<AlertRule> findByServiceIdAndCondition(UUID serviceId, AlertCondition condition);
    Optional<AlertRule> findByOrganizationIdAndId(UUID organizationId, UUID id);
}
