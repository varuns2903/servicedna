package com.servicedna.alert.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.dto.CreateAlertRuleRequest;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.service.dto.CreateServiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private String orgId;
    private String serviceId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "alertuser-" + UUID.randomUUID() + "@example.com";

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
                .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Alert Org"))))
                .andReturn().getResponse().getContentAsString();
        orgId = objectMapper.readTree(orgRes).get("id").asText();

        // Create Service
        String srvRes = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("Payment API", null, null, "us-east-1", null))))
                .andReturn().getResponse().getContentAsString();
        serviceId = objectMapper.readTree(srvRes).get("id").asText();
    }

    @Test
    void shouldCreateAndListAlertRules() throws Exception {
        CreateAlertRuleRequest req = new CreateAlertRuleRequest(
                AlertCondition.STATUS_DOWN, "https://webhook.site/my-hook", com.servicedna.alert.domain.IntegrationType.SLACK, null, null, null);

        String ruleRes = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services/" + serviceId + "/alert-rules")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.condition").value("STATUS_DOWN"))
                .andExpect(jsonPath("$.webhookUrl").value("https://webhook.site/my-hook"))
                .andReturn().getResponse().getContentAsString();
                
        String ruleId = objectMapper.readTree(ruleRes).get("id").asText();
        
        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/services/" + serviceId + "/alert-rules")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ruleId));
                
        mockMvc.perform(delete("/api/v1/organizations/" + orgId + "/services/" + serviceId + "/alert-rules/" + ruleId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());
                
        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/services/" + serviceId + "/alert-rules")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void nonMemberCannotAccessOrCreateAlertRulesInAnotherOrg() throws Exception {
        String outsiderEmail = "alert-outsider-" + UUID.randomUUID() + "@example.com";
        String outsiderRes = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(outsiderEmail, "password123"))))
                .andReturn().getResponse().getContentAsString();
        String outsiderToken = objectMapper.readTree(outsiderRes).get("token").asText();

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/services/" + serviceId + "/alert-rules")
                .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        CreateAlertRuleRequest req = new CreateAlertRuleRequest(
                AlertCondition.STATUS_DOWN, "https://webhook.site/should-not-be-created", com.servicedna.alert.domain.IntegrationType.SLACK, null, null, null);
        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services/" + serviceId + "/alert-rules")
                .header("Authorization", "Bearer " + outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/services/" + serviceId + "/alert-rules"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCreateIncidentOnlyRuleWithoutWebhook() throws Exception {
        CreateAlertRuleRequest req = new CreateAlertRuleRequest(
                AlertCondition.STATUS_DOWN, null, null, com.servicedna.incident.domain.IncidentSeverity.CRITICAL, null, null);

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services/" + serviceId + "/alert-rules")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentSeverity").value("CRITICAL"))
                .andExpect(jsonPath("$.webhookUrl").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void shouldRejectRuleWithNoAction() throws Exception {
        CreateAlertRuleRequest req = new CreateAlertRuleRequest(AlertCondition.STATUS_DOWN, null, null, null, null, null);

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services/" + serviceId + "/alert-rules")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RULE_HAS_NO_ACTION"));
    }

    @Test
    void shouldRejectIncidentSeverityOnRecoveryRule() throws Exception {
        CreateAlertRuleRequest req = new CreateAlertRuleRequest(
                AlertCondition.STATUS_RECOVERED, null, null, com.servicedna.incident.domain.IncidentSeverity.MINOR, null, null);

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services/" + serviceId + "/alert-rules")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_RULE"));
    }

    @Test
    void shouldRequireThresholdForThresholdConditions() throws Exception {
        CreateAlertRuleRequest req = new CreateAlertRuleRequest(
                AlertCondition.LATENCY_ABOVE, null, null, com.servicedna.incident.domain.IncidentSeverity.MINOR, null, 5);

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services/" + serviceId + "/alert-rules")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("LATENCY_ABOVE requires a threshold."));
    }

    @Test
    void shouldRejectErrorRateAbove100Percent() throws Exception {
        CreateAlertRuleRequest req = new CreateAlertRuleRequest(
                AlertCondition.ERROR_RATE_ABOVE, null, null, com.servicedna.incident.domain.IncidentSeverity.MINOR, 150.0, 5);

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services/" + serviceId + "/alert-rules")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_RULE"));
    }

    @Test
    void shouldCreateLatencyRuleWithThresholdAndWindow() throws Exception {
        CreateAlertRuleRequest req = new CreateAlertRuleRequest(
                AlertCondition.LATENCY_ABOVE, null, null, com.servicedna.incident.domain.IncidentSeverity.MINOR, 800.0, 10);

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services/" + serviceId + "/alert-rules")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.threshold").value(800.0))
                .andExpect(jsonPath("$.windowMinutes").value(10))
                .andExpect(jsonPath("$.breached").value(false));
    }
}
