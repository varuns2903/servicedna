package com.servicedna.service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.ServiceDnaApplication;
import com.servicedna.auth.dto.LoginRequest;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.service.domain.MaintenanceStatus;
import com.servicedna.service.dto.CreateMaintenanceWindowRequest;
import com.servicedna.service.dto.CreateServiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ServiceDnaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MaintenanceWindowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private String orgId;
    private String serviceId;

    @BeforeEach
    void setUp() throws Exception {
        String uniqueEmail = "maint-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(uniqueEmail, "password123"))))
                .andExpect(status().isCreated());

        String loginRes = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(uniqueEmail, "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        userToken = objectMapper.readTree(loginRes).get("token").asText();

        String orgRes = mockMvc.perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Maint Org"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        orgId = objectMapper.readTree(orgRes).get("id").asText();

        String srvRes = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("DatabaseService", "Database", "http://repo", "us-east-1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        serviceId = objectMapper.readTree(srvRes).get("id").asText();
    }

    @Test
    void shouldCreateAndListMaintenanceWindows() throws Exception {
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        OffsetDateTime end = start.plusHours(2);
        
        CreateMaintenanceWindowRequest req = new CreateMaintenanceWindowRequest(
                UUID.fromString(serviceId),
                "Database Upgrade",
                "Upgrading to PostgreSQL 16",
                start,
                end
        );

        String createRes = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/maintenance")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Database Upgrade"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString();
                
        String windowId = objectMapper.readTree(createRes).get("id").asText();

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/maintenance")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Database Upgrade"));

        // Update status to IN_PROGRESS
        mockMvc.perform(put("/api/v1/organizations/" + orgId + "/maintenance/" + windowId + "/status")
                .param("status", "IN_PROGRESS")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }
}
