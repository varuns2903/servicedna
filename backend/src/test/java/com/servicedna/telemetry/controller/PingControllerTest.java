package com.servicedna.telemetry.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.service.dto.CreateServiceRequest;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.telemetry.dto.PingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String apiKey;
    private String userToken;
    private String orgId;
    private String serviceId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "pinguser-" + UUID.randomUUID() + "@example.com";

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
                .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Ping Org"))))
                .andReturn().getResponse().getContentAsString();
        orgId = objectMapper.readTree(orgRes).get("id").asText();

        // Create Service
        String srvRes = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("DB Server", null, null, "us-east-1"))))
                .andReturn().getResponse().getContentAsString();
        
        serviceId = objectMapper.readTree(srvRes).get("id").asText();
        apiKey = objectMapper.readTree(srvRes).get("apiKey").asText();
    }

    @Test
    void shouldProcessPingAndUpdateStatus() throws Exception {
        PingRequest req = new PingRequest(ServiceStatus.HEALTHY, 45, "OK");

        mockMvc.perform(post("/api/v1/ping")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify status updated in service
        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/services/" + serviceId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HEALTHY"));
    }

    @Test
    void shouldRejectInvalidApiKey() throws Exception {
        PingRequest req = new PingRequest(ServiceStatus.HEALTHY, 45, "OK");

        mockMvc.perform(post("/api/v1/ping")
                .header("X-API-Key", "invalid-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_API_KEY"));
    }
}
