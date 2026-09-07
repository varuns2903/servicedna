package com.servicedna.incident.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.incident.domain.IncidentStatus;
import com.servicedna.incident.dto.CreateIncidentRequest;
import com.servicedna.incident.dto.UpdateIncidentStatusRequest;
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

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private String orgId;
    private String serviceId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "incidentuser-" + UUID.randomUUID() + "@example.com";

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
                .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Incident Org"))))
                .andReturn().getResponse().getContentAsString();
        orgId = objectMapper.readTree(orgRes).get("id").asText();

        // Create Service
        String srvRes = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("Web Frontend", null, null, "us-east-1"))))
                .andReturn().getResponse().getContentAsString();
        serviceId = objectMapper.readTree(srvRes).get("id").asText();
    }

    @Test
    void shouldCreateIncidentAndLinkService() throws Exception {
        CreateIncidentRequest req = new CreateIncidentRequest(
                "API Down",
                "The web frontend cannot reach the API",
                IncidentSeverity.CRITICAL,
                List.of(UUID.fromString(serviceId))
        );

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/incidents")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("API Down"))
                .andExpect(jsonPath("$.status").value("INVESTIGATING"))
                .andExpect(jsonPath("$.severity").value("CRITICAL"))
                .andExpect(jsonPath("$.affectedServiceIds[0]").value(serviceId));
    }

    @Test
    void shouldUpdateIncidentStatusToResolved() throws Exception {
        // Create
        CreateIncidentRequest req = new CreateIncidentRequest(
                "Minor Lag",
                "Queries are slow",
                IncidentSeverity.MINOR,
                List.of()
        );

        String incRes = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/incidents")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String incId = objectMapper.readTree(incRes).get("id").asText();

        // Update to RESOLVED
        UpdateIncidentStatusRequest updateReq = new UpdateIncidentStatusRequest(IncidentStatus.RESOLVED);

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incId + "/status")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists());
    }
}
