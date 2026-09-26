package com.servicedna.testrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.graph.domain.Protocol;
import com.servicedna.ingestion.dto.CreateIngestionKeyRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.service.dto.CreateServiceRequest;
import com.servicedna.traces.TraceDto;
import com.servicedna.traces.TraceQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
    "runner.long-poll-ms=50", "test-runs.settle-ms=0", "test-runs.min-wait-ms=0",
    "test-runs.complete-check-ms=3600000", "test-runs.sweep-ms=3600000", "test-runs.evaluate-ms=3600000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestSuiteTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TestRunCompleter completer;
  @Autowired private TestSuiteService suites;
  @MockBean private TraceQueryService traces;

  private String token;
  private String orgId;
  private String key;

  @BeforeEach
  void setUp() throws Exception {
    token = call(post("/api/v1/auth/register").content(json(new RegisterRequest("suite-" + UUID.randomUUID() + "@example.com", "password123")))).get("token").asText();
    orgId = call(post("/api/v1/organizations").content(json(new CreateOrganizationRequest("Suite Org")))).get("id").asText();
    key = call(post("/api/v1/organizations/" + orgId + "/ingestion-keys").content(json(new CreateIngestionKeyRequest("r")))).get("key").asText();
    call(post("/api/v1/organizations/" + orgId + "/services").content(json(new CreateServiceRequest("gateway", null, null, null, "http://gateway:4000/health"))));
    when(traces.trace(any(UUID.class), anyString())).thenReturn(new TraceDto.Trace("t", Instant.now(), 5, List.of(
        new TraceDto.Span("1", null, "gateway", "POST /api/orders", "SERVER", Instant.now(), 5, false, null,
            Map.of("http.response.status_code", "201", "sdna.response.body", "{\"total\":59}"), List.of()))));
  }

  private TestRunDto.Case order(String name, String assertions) throws Exception {
    return new TestRunDto.Case(name,
        new TestRunDto.CreateRequest(null, Protocol.HTTP, null, "gateway", "POST", "/api/orders", null, null, null, null, "{}", null),
        objectMapper.readTree(assertions));
  }

  @Test
  void aCollectionRunsAsASuiteAndItsAssertionsDecideTheResult() throws Exception {
    JsonNode collection = call(post("/api/v1/organizations/" + orgId + "/test-collections").content(json(new TestRunDto.SaveCollection(
        "Checkout", "happy path and a wrong expectation", List.of(
            order("totals are right", "[{\"target\":{\"service\":\"gateway\"},\"status\":201,\"response\":{\"total\":59}}]"),
            order("expects the wrong total", "[{\"target\":{\"service\":\"gateway\"},\"response\":{\"total\":60}}]"))))));
    assertThat(collection.get("cases")).hasSize(2);

    JsonNode suite = call(post("/api/v1/organizations/" + orgId + "/test-suites").content("{\"collectionId\":\"" + collection.get("id").asText() + "\"}"));
    assertThat(suite.get("status").asText()).isEqualTo("RUNNING");
    assertThat(suite.get("runs")).hasSize(2);

    for (int i = 0; i < 2; i++) {
      JsonNode job = objectMapper.readTree(mockMvc.perform(post("/api/v1/runner/claim").header("x-servicedna-key", key)
          .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getContentAsString());
      mockMvc.perform(post("/api/v1/runner/runs/" + job.get("id").asText() + "/result").header("x-servicedna-key", key)
          .contentType(MediaType.APPLICATION_JSON)
          .content(json(new TestRunDto.JobResult(true, null, 201, Map.of(), "{\"total\":59}", 5L, null, null))));
    }
    completer.completeSettledRuns();
    suites.evaluate();

    JsonNode done = call(get("/api/v1/organizations/" + orgId + "/test-suites/" + suite.get("id").asText()));
    assertThat(done.get("status").asText()).isEqualTo("FAILED");
    assertThat(done.get("passed").asInt()).isEqualTo(1);
    assertThat(done.get("failed").asInt()).isEqualTo(1);
    JsonNode wrong = done.get("runs").get(1).get("caseName").asText().startsWith("expects") ? done.get("runs").get(1) : done.get("runs").get(0);
    assertThat(wrong.get("assertionResults").get(0).get("message").asText()).isEqualTo("got 59");
  }

  @Test
  void inlineCasesRunToo_andAFailedSendFailsItsCase() throws Exception {
    JsonNode suite = call(post("/api/v1/organizations/" + orgId + "/test-suites").content(json(new TestRunDto.StartSuite(
        "CI", null, null, List.of(order("unreachable", "[]"))))));
    JsonNode job = objectMapper.readTree(mockMvc.perform(post("/api/v1/runner/claim").header("x-servicedna-key", key)
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getContentAsString());
    mockMvc.perform(post("/api/v1/runner/runs/" + job.get("id").asText() + "/result").header("x-servicedna-key", key)
        .contentType(MediaType.APPLICATION_JSON).content(json(new TestRunDto.JobResult(false, "connection refused", null, null, null, null, null, null))));
    suites.evaluate();

    JsonNode done = call(get("/api/v1/organizations/" + orgId + "/test-suites/" + suite.get("id").asText()));
    assertThat(done.get("status").asText()).isEqualTo("FAILED");
    assertThat(done.get("runs").get(0).get("assertionResults").get(0).get("message").asText()).isEqualTo("FAILED: connection refused");
  }

  private String json(Object body) throws Exception {
    return objectMapper.writeValueAsString(body);
  }

  private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
    request.contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token);
    return objectMapper.readTree(mockMvc.perform(request).andReturn().getResponse().getContentAsString());
  }
}
