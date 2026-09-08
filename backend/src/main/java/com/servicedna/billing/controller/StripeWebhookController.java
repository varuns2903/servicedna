package com.servicedna.billing.controller;

import com.servicedna.billing.domain.PlanType;
import com.servicedna.billing.domain.SubscriptionStatus;
import com.servicedna.billing.repository.SubscriptionRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/stripe")
public class StripeWebhookController {

  private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

  @Value("${stripe.webhook-secret}")
  private String webhookSecret;

  private final SubscriptionRepository subscriptionRepository;

  public StripeWebhookController(SubscriptionRepository subscriptionRepository) {
    this.subscriptionRepository = subscriptionRepository;
  }

  @PostMapping
  public ResponseEntity<String> handleWebhook(
      @RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {
    Event event;

    try {
      event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
    } catch (SignatureVerificationException e) {
      log.error("Stripe signature verification failed");
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
    } catch (Exception e) {
      log.error("Failed to parse Stripe webhook event", e);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid payload");
    }

    EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
    StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);

    if (stripeObject == null) {
      return ResponseEntity.ok("Received but unhandled (deserialization failed)");
    }

    switch (event.getType()) {
      case "checkout.session.completed":
        if (stripeObject instanceof Session session) {
          handleCheckoutSessionCompleted(session);
        }
        break;

      case "customer.subscription.updated":
        if (stripeObject instanceof com.stripe.model.Subscription stripeSubscription) {
          handleSubscriptionUpdated(stripeSubscription);
        }
        break;

      case "customer.subscription.deleted":
        if (stripeObject instanceof com.stripe.model.Subscription stripeSubscription) {
          handleSubscriptionDeleted(stripeSubscription);
        }
        break;

      default:
        log.info("Unhandled event type: {}", event.getType());
    }

    return ResponseEntity.ok("Success");
  }

  private void handleCheckoutSessionCompleted(Session session) {
    String orgIdStr = session.getMetadata().get("organizationId");
    String planTypeStr = session.getMetadata().get("planType");

    if (orgIdStr == null || planTypeStr == null) {
      log.warn("Checkout session missing metadata");
      return;
    }

    UUID organizationId = UUID.fromString(orgIdStr);
    PlanType planType = PlanType.valueOf(planTypeStr);

    subscriptionRepository
        .findByOrganizationId(organizationId)
        .ifPresent(
            sub -> {
              sub.setStripeCustomerId(session.getCustomer());
              sub.setStripeSubscriptionId(session.getSubscription());
              sub.setPlanType(planType);
              sub.setStatus(SubscriptionStatus.ACTIVE);
              subscriptionRepository.save(sub);
            });
  }

  private void handleSubscriptionUpdated(com.stripe.model.Subscription stripeSubscription) {
    subscriptionRepository
        .findByStripeSubscriptionId(stripeSubscription.getId())
        .ifPresent(
            sub -> {
              String stripeStatus = stripeSubscription.getStatus();
              if ("active".equals(stripeStatus) || "trialing".equals(stripeStatus)) {
                sub.setStatus(SubscriptionStatus.ACTIVE);
              } else if ("past_due".equals(stripeStatus) || "unpaid".equals(stripeStatus)) {
                sub.setStatus(SubscriptionStatus.PAST_DUE);
              } else if ("canceled".equals(stripeStatus)) {
                sub.setStatus(SubscriptionStatus.CANCELED);
              }

              sub.setCurrentPeriodEnd(
                  OffsetDateTime.ofInstant(
                      Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodEnd()),
                      ZoneId.systemDefault()));

              subscriptionRepository.save(sub);
            });
  }

  private void handleSubscriptionDeleted(com.stripe.model.Subscription stripeSubscription) {
    subscriptionRepository
        .findByStripeSubscriptionId(stripeSubscription.getId())
        .ifPresent(
            sub -> {
              sub.setStatus(SubscriptionStatus.CANCELED);
              sub.setPlanType(PlanType.FREE);
              sub.setStripeSubscriptionId(null);
              subscriptionRepository.save(sub);
            });
  }
}
