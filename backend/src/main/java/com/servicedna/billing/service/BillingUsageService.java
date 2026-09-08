package com.servicedna.billing.service;

import com.servicedna.billing.domain.PlanType;
import com.servicedna.billing.domain.Subscription;
import com.servicedna.billing.domain.SubscriptionStatus;
import com.servicedna.billing.repository.SubscriptionRepository;
import com.servicedna.service.repository.ServiceRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.UsageRecord;
import com.stripe.param.UsageRecordCreateOnSubscriptionItemParams;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingUsageService {

  private static final Logger log = LoggerFactory.getLogger(BillingUsageService.class);

  private final SubscriptionRepository subscriptionRepository;
  private final ServiceRepository serviceRepository;
  private final String stripeApiKey;

  public BillingUsageService(
      SubscriptionRepository subscriptionRepository,
      ServiceRepository serviceRepository,
      @Value("${stripe.api-key}") String stripeApiKey) {
    this.subscriptionRepository = subscriptionRepository;
    this.serviceRepository = serviceRepository;
    this.stripeApiKey = stripeApiKey;
  }

  @PostConstruct
  public void init() {
    Stripe.apiKey = this.stripeApiKey;
  }

  // Run every day at midnight to report usage (e.g. number of active services)
  @Scheduled(cron = "0 0 0 * * *")
  @Transactional(readOnly = true)
  public void reportUsageToStripe() {
    log.info("Starting daily usage reporting to Stripe...");

    List<Subscription> activeSubscriptions =
        subscriptionRepository.findAll().stream()
            .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
            .filter(s -> s.getStripeSubscriptionId() != null && s.getPlanType() != PlanType.FREE)
            .toList();

    for (Subscription sub : activeSubscriptions) {
      try {
        // Count how many services this organization has
        long activeServiceCount =
            serviceRepository.findByOrganizationId(sub.getOrganization().getId()).size();

        // Note: In a real implementation, you would fetch the specific subscription item ID for the
        // metered product.
        // For demonstration, we assume we resolve the item ID.
        String subscriptionItemId = resolveMeteredSubscriptionItem(sub.getStripeSubscriptionId());

        if (subscriptionItemId != null) {
          UsageRecordCreateOnSubscriptionItemParams params =
              UsageRecordCreateOnSubscriptionItemParams.builder()
                  .setQuantity(activeServiceCount)
                  .setTimestamp(Instant.now().getEpochSecond())
                  .setAction(UsageRecordCreateOnSubscriptionItemParams.Action.SET)
                  .build();

          UsageRecord.createOnSubscriptionItem(subscriptionItemId, params, null);
          log.info(
              "Successfully reported usage of {} services for organization {}",
              activeServiceCount,
              sub.getOrganization().getId());
        }

      } catch (StripeException e) {
        log.error(
            "Failed to report usage for subscription {}: {}",
            sub.getStripeSubscriptionId(),
            e.getMessage());
      } catch (Exception e) {
        log.error(
            "Unexpected error reporting usage for org {}: {}",
            sub.getOrganization().getId(),
            e.getMessage());
      }
    }

    log.info("Completed daily usage reporting to Stripe.");
  }

  /**
   * Helper to resolve the subscription item ID for the metered price. In a real app, this would
   * iterate through `com.stripe.model.Subscription.retrieve(id).getItems()` and find the one that
   * matches the metered price ID.
   */
  private String resolveMeteredSubscriptionItem(String subscriptionId) throws StripeException {
    if (subscriptionId == null || subscriptionId.startsWith("sub_test_mock")) {
      return null; // Mock for testing
    }

    com.stripe.model.Subscription stripeSub =
        com.stripe.model.Subscription.retrieve(subscriptionId);
    return stripeSub.getItems().getData().stream()
        .filter(item -> "metered".equals(item.getPrice().getRecurring().getUsageType()))
        .map(com.stripe.model.SubscriptionItem::getId)
        .findFirst()
        .orElse(null);
  }
}
