package com.servicedna.billing.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import com.servicedna.billing.domain.Subscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
  Optional<Subscription> findByOrganizationId(UUID organizationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Subscription s where s.organization.id = :organizationId")
  Optional<Subscription> findForUpdateByOrganizationId(UUID organizationId);

  Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);

  Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}
