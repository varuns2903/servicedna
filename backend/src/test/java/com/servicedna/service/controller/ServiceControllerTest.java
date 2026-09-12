package com.servicedna.service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.service.dto.AddDependencyRequest;
import com.servicedna.service.dto.CreateServiceRequest;
import com.servicedna.service.dto.UpdateServiceRequest;
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

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        CreateServiceRequest request = new CreateServiceRequest("PaymentService", "Handles payments", "https://github.com/acme/payment", "us-east-1", null);

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
        CreateServiceRequest request = new CreateServiceRequest("BillingService", "Handles billing", null, "us-east-1", null);

        mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user2Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void user2CannotListOrGetServicesInOrg1() throws Exception {
        mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateServiceRequest("PrivateService", null, null, "us-east-1", null))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/" + org1Id + "/services"))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiKeyIsOnlyReturnedOnCreationNotOnSubsequentReads() throws Exception {
        String createRes = mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateServiceRequest("KeyRedactionService", null, null, "us-east-1", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").isString())
                .andReturn().getResponse().getContentAsString();
        String serviceId = objectMapper.readTree(createRes).get("id").asText();

        // Any subsequent read — even by the same member who created it — must not re-expose the
        // key, since it's a bearer credential for that service's telemetry endpoint.
        mockMvc.perform(get("/api/v1/organizations/" + org1Id + "/services/" + serviceId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        String listRes = mockMvc.perform(get("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        boolean found = false;
        for (com.fasterxml.jackson.databind.JsonNode node : objectMapper.readTree(listRes)) {
            if (node.get("id").asText().equals(serviceId)) {
                found = true;
                org.junit.jupiter.api.Assertions.assertTrue(node.get("apiKey").isNull());
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(found, "expected the created service to be in the list");
    }

    @Test
    void shouldMapServiceDependencies() throws Exception {
        // Create Service A
        String resA = mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("ServiceA", null, null, "us-east-1", null))))
                .andReturn().getResponse().getContentAsString();
        String serviceAId = objectMapper.readTree(resA).get("id").asText();

        // Create Service B
        String resB = mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("ServiceB", null, null, "us-east-1", null))))
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
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("SelfRefService", null, null, "us-east-1", null))))
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

    @Test
    void shouldGetServiceMap() throws Exception {
        String resA = mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("MapServiceA", null, null, "us-east-1", null))))
                .andReturn().getResponse().getContentAsString();
        String serviceAId = objectMapper.readTree(resA).get("id").asText();

        String resB = mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("MapServiceB", null, null, "us-east-1", null))))
                .andReturn().getResponse().getContentAsString();
        String serviceBId = objectMapper.readTree(resB).get("id").asText();

        // A depends on B
        AddDependencyRequest depReq = new AddDependencyRequest(UUID.fromString(serviceBId));
        mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services/" + serviceAId + "/dependencies")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(depReq)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/organizations/" + org1Id + "/services/map")
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes[0].id").exists())
                .andExpect(jsonPath("$.edges").isArray())
                .andExpect(jsonPath("$.edges[0].sourceId").value(serviceAId))
                .andExpect(jsonPath("$.edges[0].targetId").value(serviceBId));
    }

    @Test
    void shouldUpdateDeleteAndRegenerateApiKey() throws Exception {
        String createRes = mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("EditableService", null, null, "us-east-1", null))))
                .andReturn().getResponse().getContentAsString();
        String serviceId = objectMapper.readTree(createRes).get("id").asText();
        String originalApiKey = objectMapper.readTree(createRes).get("apiKey").asText();

        UpdateServiceRequest updateReq =
                new UpdateServiceRequest("RenamedService", "new description", null, "eu-west-1", null, null);
        mockMvc.perform(put("/api/v1/organizations/" + org1Id + "/services/" + serviceId)
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("RenamedService"))
                .andExpect(jsonPath("$.region").value("eu-west-1"))
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        // Another org's member cannot edit it.
        mockMvc.perform(put("/api/v1/organizations/" + org1Id + "/services/" + serviceId)
                .header("Authorization", "Bearer " + user2Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        String regenRes = mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services/" + serviceId + "/api-key/regenerate")
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").exists())
                .andReturn().getResponse().getContentAsString();
        String newApiKey = objectMapper.readTree(regenRes).get("apiKey").asText();
        org.assertj.core.api.Assertions.assertThat(newApiKey).isNotEqualTo(originalApiKey);

        // The old key no longer authenticates a ping.
        mockMvc.perform(post("/api/v1/ping")
                .header("X-API-Key", originalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PingRequest(ServiceStatus.HEALTHY, 20, null))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/organizations/" + org1Id + "/services/" + serviceId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/organizations/" + org1Id + "/services/" + serviceId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldComputeUptimeAndLatencyFromPings() throws Exception {
        String createRes = mockMvc.perform(post("/api/v1/organizations/" + org1Id + "/services")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateServiceRequest("MetricsService", null, null, "us-east-1", null))))
                .andReturn().getResponse().getContentAsString();
        String serviceId = objectMapper.readTree(createRes).get("id").asText();
        String apiKey = objectMapper.readTree(createRes).get("apiKey").asText();

        mockMvc.perform(post("/api/v1/ping")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PingRequest(ServiceStatus.HEALTHY, 40, null))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ping")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PingRequest(ServiceStatus.HEALTHY, 60, null))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ping")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PingRequest(ServiceStatus.DOWN, null, "timeout"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/organizations/" + org1Id + "/services/" + serviceId + "/metrics")
                .param("range", "24h")
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pingCount").value(3))
                .andExpect(jsonPath("$.uptimePercentage").value(closeTo(66.67, 0.1)))
                .andExpect(jsonPath("$.avgLatencyMs").value(50.0))
                .andExpect(jsonPath("$.dataPoints.length()").value(3));

        mockMvc.perform(get("/api/v1/organizations/" + org1Id + "/services/" + serviceId + "/metrics")
                .param("range", "not-a-real-range")
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_RANGE"));
    }
}
