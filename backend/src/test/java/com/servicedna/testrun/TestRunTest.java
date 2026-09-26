package com.servicedna.testrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.graph.domain.Protocol;
import com.servicedna.ingestion.dto.CreateIngestionKeyRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.service.dto.CreateServiceRequest;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"runner.long-poll-ms=100", "runner.shared-token=shared-secret", "test-runs.sweep-ms=3600000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestRunTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TestRunService testRunService;
  @Autowired private EntityManager entityManager;
  @Autowired private TransactionTemplate transactions;

  private String token;
  private String orgId;
  private String key;
  private String serviceId;

  @BeforeEach
  void setUp() throws Exception {
    token = call(post("/api/v1/auth/register").content(json(new RegisterRequest("runs-" + UUID.randomUUID() + "@example.com", "password123")))).get("token").asText();
    orgId = call(post("/api/v1/organizations").content(json(new CreateOrganizationRequest("Runs Org")))).get("id").asText();
    key = call(post("/api/v1/organizations/" + orgId + "/ingestion-keys").content(json(new CreateIngestionKeyRequest("runner")))).get("key").asText();
    serviceId = call(post("/api/v1/organizations/" + orgId + "/services")
        .content(json(new CreateServiceRequest("order-service", null, null, null, "http://order-service:4004/health")))).get("id").asText();
  }

  private JsonNode createHttpRun(String path) throws Exception {
    return call(post("/api/v1/organizations/" + orgId + "/test-runs").content(json(new TestRunDto.CreateRequest(
        null, Protocol.HTTP, UUID.fromString(serviceId), "post", path, null, null, null, Map.of("x-test", "1"), "{\"userId\":\"u-1\"}"))));
  }

  @Test
  void aRunTargetsTheServicesAddressAndGetsItsOwnTrace() throws Exception {
    JsonNode run = createHttpRun("/orders");
    assertThat(run.get("status").asText()).isEqualTo("QUEUED");
    assertThat(run.get("traceId").asText()).matches("[0-9a-f]{32}");
    assertThat(run.get("serviceName").asText()).isEqualTo("order-service");
    assertThat(run.get("target").get("baseUrl").asText()).isEqualTo("http://order-service:4004");
    assertThat(run.get("target").get("method").asText()).isEqualTo("POST");
    assertThat(run.get("request").get("body").asText()).isEqualTo("{\"userId\":\"u-1\"}");
  }

  @Test
  void pathsMustBeConcrete() throws Exception {
    mockMvc.perform(auth(post("/api/v1/organizations/" + orgId + "/test-runs")).content(json(new TestRunDto.CreateRequest(
            null, Protocol.HTTP, UUID.fromString(serviceId), "GET", "/orders/{id}", null, null, null, null, null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_TEST_RUN"));
  }

  @Test
  void aRunnerClaimsItsOrganizationsRunsOnceAndReportsBack() throws Exception {
    String runId = createHttpRun("/orders").get("id").asText();

    JsonNode job = runnerCall(post("/api/v1/runner/claim").header("x-servicedna-key", key).content("{\"runner\":\"r1\"}"));
    assertThat(job.get("id").asText()).isEqualTo(runId);
    assertThat(job.get("target").get("path").asText()).isEqualTo("/orders");
    mockMvc.perform(post("/api/v1/runner/claim").header("x-servicedna-key", key).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isNoContent());

    mockMvc.perform(post("/api/v1/runner/runs/" + runId + "/result").header("x-servicedna-key", key).contentType(MediaType.APPLICATION_JSON)
            .content(json(new TestRunDto.JobResult(true, null, 201, Map.of("content-type", "application/json"), "{\"id\":\"o-1\"}", 42L, null, null))))
        .andExpect(status().isNoContent());
    JsonNode run = call(get("/api/v1/organizations/" + orgId + "/test-runs/" + runId));
    assertThat(run.get("status").asText()).isEqualTo("WAITING");
    assertThat(run.get("runner").asText()).isEqualTo("r1");
    assertThat(run.get("result").get("status").asInt()).isEqualTo(201);
  }

  @Test
  void anotherOrganizationsRunnerCantSeeTheRun() throws Exception {
    createHttpRun("/orders");
    String otherToken = call(post("/api/v1/auth/register").content(json(new RegisterRequest("other-" + UUID.randomUUID() + "@example.com", "password123")))).get("token").asText();
    String otherOrg = objectMapper.readTree(mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer " + otherToken)
        .contentType(MediaType.APPLICATION_JSON).content(json(new CreateOrganizationRequest("Other")))).andReturn().getResponse().getContentAsString()).get("id").asText();
    String otherKey = objectMapper.readTree(mockMvc.perform(post("/api/v1/organizations/" + otherOrg + "/ingestion-keys").header("Authorization", "Bearer " + otherToken)
        .contentType(MediaType.APPLICATION_JSON).content(json(new CreateIngestionKeyRequest("r")))).andReturn().getResponse().getContentAsString()).get("key").asText();

    mockMvc.perform(post("/api/v1/runner/claim").header("x-servicedna-key", otherKey).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isNoContent());
    mockMvc.perform(post("/api/v1/runner/claim").header("X-Runner-Token", "wrong").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    // The shared (self-hosted) runner serves every organization.
    assertThat(runnerCall(post("/api/v1/runner/claim").header("X-Runner-Token", "shared-secret").content("{}")).get("organizationId").asText())
        .isNotBlank();
  }

  @Test
  void aFailedSendFailsTheRun() throws Exception {
    String runId = createHttpRun("/orders").get("id").asText();
    runnerCall(post("/api/v1/runner/claim").header("x-servicedna-key", key).content("{}"));
    mockMvc.perform(post("/api/v1/runner/runs/" + runId + "/result").header("x-servicedna-key", key).contentType(MediaType.APPLICATION_JSON)
        .content(json(new TestRunDto.JobResult(false, "connection refused", null, null, null, null, null, null))));

    JsonNode run = call(get("/api/v1/organizations/" + orgId + "/test-runs/" + runId));
    assertThat(run.get("status").asText()).isEqualTo("FAILED");
    assertThat(run.get("error").asText()).isEqualTo("connection refused");
  }

  @Test
  void runsNobodyPicksUpTimeOut() throws Exception {
    String runId = createHttpRun("/orders").get("id").asText();
    transactions.executeWithoutResult(s -> entityManager.createNativeQuery("update test_runs set created_at = :t where id = :id")
        .setParameter("t", OffsetDateTime.now().minusMinutes(5)).setParameter("id", UUID.fromString(runId)).executeUpdate());

    testRunService.expireStale();

    JsonNode run = call(get("/api/v1/organizations/" + orgId + "/test-runs/" + runId));
    assertThat(run.get("status").asText()).isEqualTo("TIMED_OUT");
    assertThat(run.get("error").asText()).startsWith("No runner picked this run up");
  }

  private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
    return request.contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token);
  }

  private JsonNode runnerCall(MockHttpServletRequestBuilder request) throws Exception {
    request.contentType(MediaType.APPLICATION_JSON);
    return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  private String json(Object body) throws Exception {
    return objectMapper.writeValueAsString(body);
  }

  private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
    return objectMapper.readTree(mockMvc.perform(auth(request)).andReturn().getResponse().getContentAsString());
  }
}
