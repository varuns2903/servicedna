package com.servicedna.billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.billing.domain.PlanType;
import com.servicedna.billing.domain.SubscriptionStatus;
import com.servicedna.billing.dto.CheckoutSessionRequest;
import com.servicedna.billing.dto.CheckoutSessionResponse;
import com.servicedna.billing.dto.SubscriptionDto;
import com.servicedna.billing.service.BillingService;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BillingService billingService;

    private String userToken;
    private String orgId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "billinguser-" + UUID.randomUUID() + "@example.com";

        // Register User
        String res = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password123"))))
                .andReturn().getResponse().getContentAsString();
        userToken = objectMapper.readTree(res).get("token").asText();

        // Create Org
        String orgRes = mockMvc.perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Billing Org"))))
                .andReturn().getResponse().getContentAsString();
        orgId = objectMapper.readTree(orgRes).get("id").asText();
    }

    @Test
    void shouldGetSubscription() throws Exception {
        SubscriptionDto dto = new SubscriptionDto(PlanType.FREE, SubscriptionStatus.ACTIVE, OffsetDateTime.now());
        when(billingService.getSubscription(eq(UUID.fromString(orgId)), any())).thenReturn(dto);

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/billing/subscription")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planType").value("FREE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldCreateCheckoutSession() throws Exception {
        CheckoutSessionResponse resp = new CheckoutSessionResponse("https://checkout.stripe.com/test");
        when(billingService.createCheckoutSession(eq(UUID.fromString(orgId)), eq(PlanType.PRO), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/billing/checkout-session")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CheckoutSessionRequest(PlanType.PRO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://checkout.stripe.com/test"));
    }
}
