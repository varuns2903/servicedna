package com.servicedna.organization.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.ServiceDnaApplication;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ServiceDnaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private String orgId;

    @BeforeEach
    void setUp() throws Exception {
        String uniqueEmail = "audit-" + UUID.randomUUID() + "@example.com";
        // Newly registered users are unverified and login is blocked until verification, but
        // register() itself already returns a usable token, so tests that only need an
        // authenticated user (not to exercise the login endpoint itself) use that directly.
        String registerRes = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(uniqueEmail, "password123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        userToken = objectMapper.readTree(registerRes).get("token").asText();

        String orgRes = mockMvc.perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Audit Org"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        orgId = objectMapper.readTree(orgRes).get("id").asText();
    }

    @Test
    void shouldLogAndRetrieveAuditLogs() throws Exception {
        // Trigger action
        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("AuditedService", "Desc", "http://repo", "us-east-1", null))))
                .andExpect(status().isCreated());

        // We use @Async for events, so we might need to sleep slightly in test, or it could be fast enough
        Thread.sleep(500);

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/audit-logs")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("CREATE_SERVICE"))
                .andExpect(jsonPath("$.content[0].entityType").value("Service"));
    }
}
