package com.servicedna.billing.repository;

import com.servicedna.billing.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByOrganizationId(UUID organizationId);
    Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}
