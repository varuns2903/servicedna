package com.servicedna.service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.service.dto.AddDependencyRequest;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String user1Token;
    private String user2Token;
    private String org1Id;

    @BeforeEach
    void setUp() throws Exception {
        String user1Email = "serviceuser1-" + UUID.randomUUID() + "@example.com";
        String user2Email = "serviceuser2-" + UUID.randomUUID() + "@example.com";

        // Register User 1
        String res1 = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(user1Email, "password123"))))
                .andReturn().getResponse().getContentAsString();
        user1Token = objectMapper.readTree(res1).get("token").asText();

        // Register User 2
        String res2 = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(user2Email, "password123"))))
                .andReturn().getResponse().getContentAsString();
        user2Token = objectMapper.readTree(res2).get("token").asText();

        // User 1 creates Org 1
        String orgRes = mockMvc.perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Service Org 1"))))
                .andReturn().getResponse().getContentAsString();
        org1Id = objectMapper.readTree(orgRes).get("id").asText();
    }

    @Test
    void shouldCreateServiceAndGenerateApiKey() throws Exception {
        CreateServiceRequest request = new CreateServiceRequest("PaymentService", "Handles payments", "https://github.com/acme/payment", "us-east-1");

        mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("PaymentService"))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.apiKey").exists())
                .andExpect(jsonPath("$.apiKey").isString());
    }

    @Test
    void user2CannotCreateServiceInOrg1() throws Exception {
        CreateServiceRequest request = new CreateServiceRequest("BillingService", "Handles billing", null, "us-east-1");

        mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user2Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void shouldMapServiceDependencies() throws Exception {
        // Create Service A
        String resA = mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("ServiceA", null, null, "us-east-1"))))
                .andReturn().getResponse().getContentAsString();
        String serviceAId = objectMapper.readTree(resA).get("id").asText();

        // Create Service B
        String resB = mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("ServiceB", null, null, "us-east-1"))))
                .andReturn().getResponse().getContentAsString();
        String serviceBId = objectMapper.readTree(resB).get("id").asText();

        // Service A depends on Service B
        AddDependencyRequest depReq = new AddDependencyRequest(UUID.fromString(serviceBId));

        mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services/" + serviceAId + "/dependencies")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(depReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencyIds[0]").value(serviceBId));

        // Get Service A to verify
        mockMvc.perform(get("/api/v1/organizations/" + org1Id + "/services/" + serviceAId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencyIds[0]").value(serviceBId));
    }
    
    @Test
    void cannotDependOnItself() throws Exception {
        String resA = mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("SelfRefService", null, null, "us-east-1"))))
                .andReturn().getResponse().getContentAsString();
        String serviceAId = objectMapper.readTree(resA).get("id").asText();

        AddDependencyRequest depReq = new AddDependencyRequest(UUID.fromString(serviceAId));

        mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services/" + serviceAId + "/dependencies")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(depReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DEPENDENCY"));
    }
}
