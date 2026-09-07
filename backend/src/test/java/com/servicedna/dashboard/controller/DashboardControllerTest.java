package com.servicedna.dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.incident.dto.CreateIncidentRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.dto.CreateServiceRequest;
import com.servicedna.telemetry.dto.PingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private String orgId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "dashuser-" + UUID.randomUUID() + "@example.com";

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
                .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Dashboard Org"))))
                .andReturn().getResponse().getContentAsString();
        orgId = objectMapper.readTree(orgRes).get("id").asText();

        // Create Service
        String srvRes = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("API", null, null, "us-east-1"))))
                .andReturn().getResponse().getContentAsString();
        
        String serviceId = objectMapper.readTree(srvRes).get("id").asText();
        String apiKey = objectMapper.readTree(srvRes).get("apiKey").asText();

        // Send a ping to make it HEALTHY and set latency
        mockMvc.perform(post("/api/v1/ping")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PingRequest(ServiceStatus.HEALTHY, 120, "OK"))))
                .andExpect(status().isOk());

        // Create an incident
        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/incidents")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateIncidentRequest("DB Slowness", "DB is very slow", IncidentSeverity.MAJOR, List.of(UUID.fromString(serviceId))))))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldGetDashboardSummary() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/dashboard")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalServices").value(1))
                .andExpect(jsonPath("$.healthyServices").value(1))
                .andExpect(jsonPath("$.activeIncidentsCount").value(1))
                .andExpect(jsonPath("$.activeIncidents[0].title").value("DB Slowness"))
                .andExpect(jsonPath("$.servicesOverview[0].name").value("API"))
                .andExpect(jsonPath("$.servicesOverview[0].status").value("HEALTHY"))
                .andExpect(jsonPath("$.servicesOverview[0].averageLatencyMs").value(120.0));
    }
}
