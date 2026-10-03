package com.servicedna.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.AlertRule;
import com.servicedna.alert.dto.CreateAlertRuleRequest;
import com.servicedna.alert.event.ServiceStatusChangedEvent;
import com.servicedna.alert.repository.AlertRuleRepository;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.service.domain.Service;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.dto.CreateMaintenanceWindowRequest;
import com.servicedna.service.dto.CreateServiceRequest;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.telemetry.domain.ServicePing;
import com.servicedna.telemetry.repository.ServicePingRepository;
import com.servicedna.telemetry.requests.RequestStats;
import com.servicedna.ingestion.event.TraceBatchReceivedEvent;
import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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

@SpringBootTest(properties = "alert.threshold-evaluation-interval-ms=3600000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertThresholdEvaluatorTest {

  @Autowired private AlertThresholdEvaluator evaluator;

  @Autowired private AlertEventConsumer consumer;

  @Autowired private AlertRuleRepository alertRuleRepository;

  @Autowired private ServicePingRepository servicePingRepository;

  @Autowired private ServiceRepository serviceRepository;

  @Autowired private MockMvc mockMvc;

  @Autowired private RequestStats requestStats;

  @Autowired private ObjectMapper objectMapper;

  private String token;
  private String orgId;
  private UUID serviceId;

  @BeforeEach
  void setUp() throws Exception {
    token =
        call(
                post("/api/v1/auth/register")
                    .content(json(new RegisterRequest("threshold-" + UUID.randomUUID() + "@example.com", "password123"))))
            .get("token")
            .asText();
    orgId = call(post("/api/v1/organizations").content(json(new CreateOrganizationRequest("Threshold Org")))).get("id").asText();
    serviceId =
        UUID.fromString(
            call(
                    post("/api/v1/organizations/" + orgId + "/services")
                        .content(json(new CreateServiceRequest("payment-service", null, null, null, null))))
                .get("id")
                .asText());
    setStatus(ServiceStatus.HEALTHY);
  }

  @Test
  void latencyBreachOpensOneIncidentAndResolvesWhenItClears() throws Exception {
    UUID ruleId = addRule(AlertCondition.LATENCY_ABOVE, 1000.0, 5);
    pings(ServiceStatus.HEALTHY, 1500, 3);

    evaluate(ruleId);
    evaluate(ruleId); // still breached: must not fire again

    assertThat(rule(ruleId).isBreached()).isTrue();
    List<JsonNode> incidents = incidents();
    assertThat(incidents).hasSize(1);
    assertThat(incidents.get(0).get("title").asText()).isEqualTo("payment-service has high latency");
    assertThat(incidents.get(0).get("severity").asText()).isEqualTo("MAJOR");
    assertThat(incidents.get(0).get("description").asText())
        .contains("average health-check latency 1500 ms over the last 5 min is above 1000 ms");

    pings(ServiceStatus.HEALTHY, 100, 6); // average now 566 ms
    evaluate(ruleId);

    assertThat(rule(ruleId).isBreached()).isFalse();
    assertThat(incidents().get(0).get("status").asText()).isEqualTo("RESOLVED");
  }

  @Test
  void latencyIgnoresFailedChecks() throws Exception {
    UUID ruleId = addRule(AlertCondition.LATENCY_ABOVE, 1000.0, 5);
    pings(ServiceStatus.HEALTHY, 200, 3);
    pings(ServiceStatus.DOWN, 5000, 3); // timeouts: ERROR_RATE_ABOVE's job, not latency's

    evaluate(ruleId);

    assertThat(rule(ruleId).isBreached()).isFalse();
  }

  @Test
  void errorRateAboveThresholdBreaches() throws Exception {
    UUID ruleId = addRule(AlertCondition.ERROR_RATE_ABOVE, 50.0, 5);
    pings(ServiceStatus.HEALTHY, 100, 1);
    pings(ServiceStatus.DOWN, null, 2); // 66%

    evaluate(ruleId);

    assertThat(rule(ruleId).isBreached()).isTrue();
    assertThat(incidents().get(0).get("title").asText()).isEqualTo("payment-service has a high error rate");
  }

  @Test
  void consecutiveFailuresNeedsTheLatestChecksToAllFail() throws Exception {
    UUID ruleId = addRule(AlertCondition.CONSECUTIVE_FAILURES, 3.0, null);
    pings(ServiceStatus.DOWN, null, 2);
    pings(ServiceStatus.HEALTHY, 100, 1);
    pings(ServiceStatus.DOWN, null, 2);

    evaluate(ruleId);
    assertThat(rule(ruleId).isBreached()).isFalse();

    pings(ServiceStatus.DOWN, null, 1);
    evaluate(ruleId);
    assertThat(rule(ruleId).isBreached()).isTrue();
    assertThat(incidents().get(0).get("title").asText()).isEqualTo("payment-service is failing repeatedly");
  }

  @Test
  void tooFewSamplesNeverBreach() throws Exception {
    UUID ruleId = addRule(AlertCondition.LATENCY_ABOVE, 1000.0, 5);
    pings(ServiceStatus.HEALTHY, 9000, 2);

    evaluate(ruleId);

    assertThat(rule(ruleId).isBreached()).isFalse();
    assertThat(incidents()).isEmpty();
  }

  @Test
  void maintenanceHoldsTheAlertUntilItEnds() throws Exception {
    UUID ruleId = addRule(AlertCondition.LATENCY_ABOVE, 1000.0, 5);
    pings(ServiceStatus.HEALTHY, 1500, 3);
    String windowId =
        call(
                post("/api/v1/organizations/" + orgId + "/maintenance")
                    .content(
                        json(
                            new CreateMaintenanceWindowRequest(
                                serviceId,
                                "Planned upgrade",
                                null,
                                OffsetDateTime.now().minusMinutes(5),
                                OffsetDateTime.now().plusHours(1)))))
            .get("id")
            .asText();
    call(put("/api/v1/organizations/" + orgId + "/maintenance/" + windowId + "/status").param("status", "IN_PROGRESS"));

    evaluate(ruleId);
    assertThat(rule(ruleId).isBreached()).isFalse();
    assertThat(incidents()).isEmpty();

    call(put("/api/v1/organizations/" + orgId + "/maintenance/" + windowId + "/status").param("status", "COMPLETED"));
    evaluate(ruleId);
    assertThat(rule(ruleId).isBreached()).isTrue();
    assertThat(incidents()).hasSize(1);
  }

  @Test
  void statusRecoveryDoesNotResolveWhileAThresholdIsBreached() throws Exception {
    UUID ruleId = addRule(AlertCondition.LATENCY_ABOVE, 1000.0, 5);
    pings(ServiceStatus.HEALTHY, 1500, 3);
    evaluate(ruleId);

    consumer.consumeStatusChangedEvent(
        json(
            new ServiceStatusChangedEvent(
                serviceId, UUID.fromString(orgId), ServiceStatus.DEGRADED, ServiceStatus.HEALTHY, OffsetDateTime.now())));

    assertThat(incidents().get(0).get("status").asText()).isEqualTo("INVESTIGATING");
  }

  private UUID addRule(AlertCondition condition, Double threshold, Integer windowMinutes) throws Exception {
    return UUID.fromString(
        call(
                post("/api/v1/organizations/" + orgId + "/services/" + serviceId + "/alert-rules")
                    .content(
                        json(
                            new CreateAlertRuleRequest(
                                condition, null, null, IncidentSeverity.MAJOR, threshold, windowMinutes))))
            .get("id")
            .asText());
  }

  @Test
  void realTrafficErrorRateWinsOverHealthyChecks() throws Exception {
    UUID ruleId = addRule(AlertCondition.ERROR_RATE_ABOVE, 10.0, 5);
    pings(ServiceStatus.HEALTHY, 20, 5); // the health checks pass…
    requests(21, 20, false);
    requests(9, 20, true); // …but 9 of 30 real requests failed

    evaluate(ruleId);

    assertThat(rule(ruleId).isBreached()).isTrue();
    assertThat(incidents().get(0).get("description").asText())
        .contains("request error rate (30 requests) 30% over the last 5 min is above 10%");
  }

  @Test
  void latencyIsTheP95OfRealRequests() throws Exception {
    UUID ruleId = addRule(AlertCondition.LATENCY_ABOVE, 1000.0, 5);
    requests(36, 20, false);
    requests(4, 3000, false); // the slowest 10%: p95 lands among them

    evaluate(ruleId);

    assertThat(rule(ruleId).isBreached()).isTrue();
    assertThat(incidents().get(0).get("description").asText()).contains("p95 request latency (40 requests)");
  }

  @Test
  void tooLittleTrafficFallsBackToHealthChecks() throws Exception {
    UUID ruleId = addRule(AlertCondition.LATENCY_ABOVE, 1000.0, 5);
    requests(5, 20, false); // fast, but too few to judge on
    pings(ServiceStatus.HEALTHY, 1500, 3);

    evaluate(ruleId);

    assertThat(rule(ruleId).isBreached()).isTrue();
    assertThat(incidents().get(0).get("description").asText()).contains("average health-check latency 1500 ms");
  }

  /** Server spans for payment-service, as if it exported them, counted and written out. */
  private void requests(int count, long durationMs, boolean error) {
    ScopeSpans.Builder scope = ScopeSpans.newBuilder();
    long start = Instant.now().toEpochMilli() * 1_000_000;
    for (int i = 0; i < count; i++) {
      scope.addSpans(Span.newBuilder()
          .setTraceId(ByteString.copyFrom(UUID.randomUUID().toString().substring(0, 16).getBytes()))
          .setSpanId(ByteString.copyFrom(UUID.randomUUID().toString().substring(0, 8).getBytes()))
          .setKind(Span.SpanKind.SPAN_KIND_SERVER)
          .setName("POST /charge")
          .setStartTimeUnixNano(start)
          .setEndTimeUnixNano(start + durationMs * 1_000_000)
          .setStatus(Status.newBuilder().setCode(error ? Status.StatusCode.STATUS_CODE_ERROR : Status.StatusCode.STATUS_CODE_UNSET)));
    }
    KeyValue name = KeyValue.newBuilder().setKey("service.name").setValue(AnyValue.newBuilder().setStringValue("payment-service")).build();
    requestStats.onTraceBatch(new TraceBatchReceivedEvent(UUID.fromString(orgId), ExportTraceServiceRequest.newBuilder()
        .addResourceSpans(ResourceSpans.newBuilder().setResource(Resource.newBuilder().addAttributes(name)).addScopeSpans(scope))
        .build()));
    requestStats.flush();
  }

  private void evaluate(UUID ruleId) {
    AlertRule rule =
        alertRuleRepository.findWithServiceByConditionIn(List.of(AlertCondition.values())).stream()
            .filter(r -> r.getId().equals(ruleId))
            .findFirst()
            .orElseThrow();
    evaluator.evaluate(rule, OffsetDateTime.now());
  }

  private AlertRule rule(UUID ruleId) {
    return alertRuleRepository.findById(ruleId).orElseThrow();
  }

  private void pings(ServiceStatus status, Integer latencyMs, int count) throws InterruptedException {
    Service service = serviceRepository.findById(serviceId).orElseThrow();
    for (int i = 0; i < count; i++) {
      servicePingRepository.save(new ServicePing(UUID.randomUUID(), service, status, latencyMs, null));
      Thread.sleep(2); // keep created_at ordering unambiguous
    }
  }

  private void setStatus(ServiceStatus status) {
    Service service = serviceRepository.findById(serviceId).orElseThrow();
    service.setStatus(status);
    serviceRepository.save(service);
  }

  private List<JsonNode> incidents() throws Exception {
    List<JsonNode> result = new ArrayList<>();
    call(get("/api/v1/organizations/" + orgId + "/incidents")).forEach(result::add);
    return result;
  }

  private String json(Object body) throws Exception {
    return objectMapper.writeValueAsString(body);
  }

  private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
    request.contentType(MediaType.APPLICATION_JSON);
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    String body = mockMvc.perform(request).andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body);
  }
}
