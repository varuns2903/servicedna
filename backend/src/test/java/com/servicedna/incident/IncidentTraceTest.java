package com.servicedna.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.incident.dto.CreateIncidentRequest;
import com.servicedna.incident.domain.IncidentSeverity;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IncidentTraceTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private TraceQueryService traces;

  private String token;
  private String orgId;
  private String incidentId;
  private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";

  @BeforeEach
  void setUp() throws Exception {
    token = call(post("/api/v1/auth/register").content(json(new RegisterRequest("inc-" + UUID.randomUUID() + "@example.com", "password123")))).get("token").asText();
    orgId = call(post("/api/v1/organizations").content(json(new CreateOrganizationRequest("Incident Org")))).get("id").asText();
    UUID payments = UUID.fromString(call(post("/api/v1/organizations/" + orgId + "/services")
        .content(json(new CreateServiceRequest("payment-service", null, null, null, null)))).get("id").asText());
    incidentId = call(post("/api/v1/organizations/" + orgId + "/incidents").content(json(new CreateIncidentRequest(
        "Checkout failing", "Payments down", IncidentSeverity.MAJOR, List.of(payments))))).get("id").asText();

    Instant t = Instant.parse("2026-09-26T10:00:00Z");
    when(traces.trace(any(UUID.class), eq(TRACE))).thenReturn(new TraceDto.Trace(TRACE, t, 40, List.of(
        new TraceDto.Span("1", null, "api-gateway", "POST /api/orders", "SERVER", t, 40, true, null,
            Map.of("http.request.method", "POST", "url.path", "/api/orders", "http.response.status_code", "502",
                "sdna.request.body", "{\"userId\":\"u-1\"}"), List.of()),
        new TraceDto.Span("2", "1", "api-gateway", "POST", "CLIENT", t, 30, true, null, Map.of(), List.of()),
        new TraceDto.Span("3", "2", "order-service", "POST /orders", "SERVER", t, 28, true, null, Map.of("http.response.status_code", "502"), List.of()),
        new TraceDto.Span("4", "3", "payment-service", "Charge", "SERVER", t, 2, true, "UNAVAILABLE", Map.of(), List.of()))));
  }

  @Test
  void failingTracesAreSearchedThroughTheAffectedServicesDuringTheIncident() throws Exception {
    when(traces.explore(any(UUID.class), isNull(), anyString(), any(Instant.class), any(Instant.class), anyInt(), any(UUID.class)))
        .thenReturn(new TraceDto.Explore("q", List.of()));
    call(get("/api/v1/organizations/" + orgId + "/incidents/" + incidentId + "/failing-traces"));

    ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
    verify(traces).explore(any(UUID.class), isNull(), query.capture(), from.capture(), any(Instant.class), eq(50), any(UUID.class));
    assertThat(query.getValue()).isEqualTo("{ status = error && (resource.service.name = \"payment-service\") }");
    assertThat(from.getValue()).isBefore(Instant.now().minusSeconds(14 * 60));
  }

  @Test
  void anAttachedTraceKeepsItsFailurePathOnTheTimeline() throws Exception {
    JsonNode attached = call(post("/api/v1/organizations/" + orgId + "/incidents/" + incidentId + "/traces")
        .content("{\"traceId\":\"" + TRACE.toUpperCase() + "\",\"note\":\"first failing checkout\"}"));
    String summary = "Trace 4bf92f35: api-gateway POST /api/orders → failed at payment-service Charge (UNAVAILABLE)";
    assertThat(attached.get("summary").asText()).isEqualTo(summary);
    assertThat(attached.get("hops")).hasSize(3);

    JsonNode events = call(get("/api/v1/organizations/" + orgId + "/incidents/" + incidentId + "/events"));
    JsonNode last = events.get(events.size() - 1);
    assertThat(last.get("eventType").asText()).isEqualTo("TRACE_ATTACHED");
    assertThat(last.get("message").asText()).isEqualTo(summary);

    mockMvc.perform(auth(post("/api/v1/organizations/" + orgId + "/incidents/" + incidentId + "/traces")
            .content("{\"traceId\":\"" + TRACE + "\"}")))
        .andExpect(status().isConflict());

    JsonNode list = call(get("/api/v1/organizations/" + orgId + "/incidents/" + incidentId + "/traces"));
    assertThat(list).hasSize(1);
    assertThat(list.get(0).get("note").asText()).isEqualTo("first failing checkout");
    assertThat(list.get(0).get("hops").get(2).get("statusMessage").asText()).isEqualTo("UNAVAILABLE");

    mockMvc.perform(auth(delete("/api/v1/organizations/" + orgId + "/incidents/" + incidentId + "/traces/" + TRACE)))
        .andExpect(status().isNoContent());
    assertThat(call(get("/api/v1/organizations/" + orgId + "/incidents/" + incidentId + "/traces"))).isEmpty();
  }

  @Test
  void aFailedRequestCanBeReplayedAsATestRun() throws Exception {
    call(post("/api/v1/organizations/" + orgId + "/services").content(json(new CreateServiceRequest("api-gateway", null, null, null, "http://gateway:4000/health"))));
    JsonNode run = call(post("/api/v1/organizations/" + orgId + "/test-runs/replay").content("{\"traceId\":\"" + TRACE + "\"}"));
    assertThat(run.get("status").asText()).isEqualTo("QUEUED");
    assertThat(run.get("serviceName").asText()).isEqualTo("api-gateway");
    assertThat(run.get("target").get("path").asText()).isEqualTo("/api/orders");
  }

  private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
    return request.contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token);
  }

  private String json(Object body) throws Exception {
    return objectMapper.writeValueAsString(body);
  }

  private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
    request.contentType(MediaType.APPLICATION_JSON);
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return objectMapper.readTree(mockMvc.perform(request).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
  }
}
