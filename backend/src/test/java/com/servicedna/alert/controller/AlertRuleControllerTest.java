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
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("Payment API", null, null))))
                .andReturn().getResponse().getContentAsString();
        serviceId = objectMapper.readTree(srvRes).get("id").asText();
    }

    @Test
    void shouldCreateAndListAlertRules() throws Exception {
        CreateAlertRuleRequest req = new CreateAlertRuleRequest(
                AlertCondition.STATUS_DOWN,
                "https://webhook.site/my-hook"
        );

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
}
