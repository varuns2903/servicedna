package com.servicedna.service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.ServiceDnaApplication;
import com.servicedna.auth.dto.LoginRequest;
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
class PublicStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String orgId;

    @BeforeEach
    void setUp() throws Exception {
        String uniqueEmail = "public-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(uniqueEmail, "password123"))))
                .andExpect(status().isCreated());

        String loginRes = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(uniqueEmail, "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String userToken = objectMapper.readTree(loginRes).get("token").asText();

        String orgRes = mockMvc.perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Public Org"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        orgId = objectMapper.readTree(orgRes).get("id").asText();

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("Web App", "Frontend", "http://repo", "us-east-1"))))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldGetPublicStatusPage() throws Exception {
        mockMvc.perform(get("/api/v1/public/organizations/" + orgId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName").value("Public Org"))
                .andExpect(jsonPath("$.overallState").value("ALL_SYSTEMS_OPERATIONAL"))
                .andExpect(jsonPath("$.services[0].name").value("Web App"))
                .andExpect(jsonPath("$.services[0].status").value("UNKNOWN"));
    }
}
