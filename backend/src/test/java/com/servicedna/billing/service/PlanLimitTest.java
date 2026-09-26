package com.servicedna.billing.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.billing.domain.PlanType;
import com.servicedna.billing.domain.Subscription;
import com.servicedna.billing.repository.SubscriptionRepository;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.service.dto.CreateServiceRequest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class PlanLimitTest {

  abstract static class Base {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SubscriptionRepository subscriptionRepository;

    String token;
    String orgId;

    @BeforeEach
    void setUp() throws Exception {
      String res =
          mockMvc
              .perform(
                  post("/api/v1/auth/register")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          objectMapper.writeValueAsString(
                              new RegisterRequest("plan-" + UUID.randomUUID() + "@example.com", "password123"))))
              .andReturn()
              .getResponse()
              .getContentAsString();
      token = objectMapper.readTree(res).get("token").asText();
      String org =
          mockMvc
              .perform(
                  post("/api/v1/organizations")
                      .header("Authorization", "Bearer " + token)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Plan Org"))))
              .andReturn()
              .getResponse()
              .getContentAsString();
      orgId = objectMapper.readTree(org).get("id").asText();
    }

    ResultActions createService(String name) throws Exception {
      return mockMvc.perform(
          post("/api/v1/organizations/" + orgId + "/services")
              .header("Authorization", "Bearer " + token)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(new CreateServiceRequest(name, null, null, null, null))));
    }

    ResultActions usage() throws Exception {
      return mockMvc.perform(
          get("/api/v1/organizations/" + orgId + "/billing/usage").header("Authorization", "Bearer " + token));
    }

    void setPlan(PlanType plan) {
      Subscription subscription =
          subscriptionRepository.findByOrganizationId(UUID.fromString(orgId)).orElseThrow();
      subscription.setPlanType(plan);
      subscriptionRepository.save(subscription);
    }
  }

  @Nested
  @SpringBootTest
  @AutoConfigureMockMvc
  @ActiveProfiles("test")
  class Enforced extends Base {

    @Test
    void freePlanStopsAtThreeServices() throws Exception {
      for (int i = 1; i <= 3; i++) {
        createService("svc-" + i).andExpect(status().isCreated());
      }
      createService("svc-4")
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.errorCode").value("PLAN_LIMIT_REACHED"))
          .andExpect(
              jsonPath("$.message")
                  .value("The Free plan allows up to 3 services. Upgrade your plan to add more."));
      usage()
          .andExpect(jsonPath("$.planType").value("FREE"))
          .andExpect(jsonPath("$.serviceCount").value(3))
          .andExpect(jsonPath("$.maxServices").value(3))
          .andExpect(jsonPath("$.pingRetentionDays").value(1))
          .andExpect(jsonPath("$.limitsEnforced").value(true));
    }

    @Test
    void proPlanAllowsMore() throws Exception {
      setPlan(PlanType.PRO);
      for (int i = 1; i <= 4; i++) {
        createService("svc-" + i).andExpect(status().isCreated());
      }
      usage().andExpect(jsonPath("$.maxServices").value(50)).andExpect(jsonPath("$.pingRetentionDays").value(30));
    }

    @Test
    void enterprisePlanIsUnlimited() throws Exception {
      setPlan(PlanType.ENTERPRISE);
      usage().andExpect(jsonPath("$.maxServices").doesNotExist()).andExpect(jsonPath("$.pingRetentionDays").value(90));
    }

    @Test
    void portalNeedsABillingAccount() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/organizations/" + orgId + "/billing/portal-session")
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("NO_BILLING_ACCOUNT"));
    }
  }

  @Nested
  @SpringBootTest(properties = "billing.enforce-plan-limits=false")
  @AutoConfigureMockMvc
  @ActiveProfiles("test")
  class NotEnforced extends Base {

    @Test
    void selfHostedDeploymentsCanTurnLimitsOff() throws Exception {
      for (int i = 1; i <= 4; i++) {
        createService("svc-" + i).andExpect(status().isCreated());
      }
      usage()
          .andExpect(jsonPath("$.maxServices").doesNotExist())
          .andExpect(jsonPath("$.pingRetentionDays").value(90))
          .andExpect(jsonPath("$.limitsEnforced").value(false));
    }
  }
}
