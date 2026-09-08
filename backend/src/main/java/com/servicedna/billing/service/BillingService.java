package com.servicedna.billing.service;

import com.servicedna.billing.domain.PlanType;
import com.servicedna.billing.domain.Subscription;
import com.servicedna.billing.dto.CheckoutSessionResponse;
import com.servicedna.billing.dto.SubscriptionDto;
import com.servicedna.billing.repository.SubscriptionRepository;
import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.service.OrganizationService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

  private final SubscriptionRepository subscriptionRepository;
  private final OrganizationService organizationService;

  @Value("${stripe.price-id.pro}")
  private String proPriceId;

  @Value("${stripe.price-id.enterprise}")
  private String enterprisePriceId;

  public BillingService(
      SubscriptionRepository subscriptionRepository, OrganizationService organizationService) {
    this.subscriptionRepository = subscriptionRepository;
    this.organizationService = organizationService;
  }

  @Transactional(readOnly = true)
  public SubscriptionDto getSubscription(UUID organizationId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    Subscription subscription =
        subscriptionRepository
            .findByOrganizationId(organizationId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "SUB_NOT_FOUND", "Subscription not found"));

    return new SubscriptionDto(
        subscription.getPlanType(), subscription.getStatus(), subscription.getCurrentPeriodEnd());
  }

  @Transactional
  public CheckoutSessionResponse createCheckoutSession(
      UUID organizationId, PlanType planType, UUID userId) {
    OrganizationMember member = organizationService.validateUserAccess(organizationId, userId);

    if (member.getRole() != OrganizationRole.OWNER) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Only organization owners can manage billing");
    }

    if (planType == PlanType.FREE) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_PLAN", "Cannot checkout for free plan");
    }

    Subscription subscription =
        subscriptionRepository
            .findByOrganizationId(organizationId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "SUB_NOT_FOUND", "Subscription not found"));

    String priceId = planType == PlanType.PRO ? proPriceId : enterprisePriceId;

    // Note: For a production app, the success and cancel URLs should point to the frontend domain.
    String successUrl = "http://localhost:5173/dashboard?session_id={CHECKOUT_SESSION_ID}";
    String cancelUrl = "http://localhost:5173/dashboard";

    try {
      SessionCreateParams.Builder paramsBuilder =
          SessionCreateParams.builder()
              .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
              .setSuccessUrl(successUrl)
              .setCancelUrl(cancelUrl)
              .putMetadata("organizationId", organizationId.toString())
              .putMetadata("planType", planType.name())
              .addLineItem(
                  SessionCreateParams.LineItem.builder().setQuantity(1L).setPrice(priceId).build());

      if (subscription.getStripeCustomerId() != null) {
        paramsBuilder.setCustomer(subscription.getStripeCustomerId());
      }

      Session session = Session.create(paramsBuilder.build());
      return new CheckoutSessionResponse(session.getUrl());

    } catch (StripeException e) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR, "STRIPE_ERROR", "Failed to create checkout session");
    }
  }
}
