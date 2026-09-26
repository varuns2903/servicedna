package com.servicedna.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.dto.CreateAlertRuleRequest;
import com.servicedna.alert.event.ServiceStatusChangedEvent;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.dto.CreateMaintenanceWindowRequest;
import com.servicedna.service.dto.AddDependencyRequest;
import com.servicedna.service.dto.CreateServiceRequest;
import com.servicedna.service.repository.ServiceRepository;
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

/** Status-change events → alert rules → incidents opened, updated, and resolved automatically. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertIncidentFlowTest {

  @Autowired private AlertEventConsumer consumer;

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ServiceRepository serviceRepository;

  private String token;
  private String orgId;
  private UUID serviceId;

  @BeforeEach
  void setUp() throws Exception {
    token =
        call(
                post("/api/v1/auth/register")
                    .content(json(new RegisterRequest("flow-" + UUID.randomUUID() + "@example.com", "password123"))))
            .get("token")
            .asText();
    orgId = call(post("/api/v1/organizations").content(json(new CreateOrganizationRequest("Flow Org")))).get("id").asText();
    serviceId = createService("payment-service");
  }

  @Test
  void cascadeJoinsTheRootCausesIncident() throws Exception {
    UUID orderId = createService("order-service");
    dependsOn(orderId, serviceId);
    addRule(serviceId, AlertCondition.STATUS_DOWN, IncidentSeverity.CRITICAL);
    addRule(orderId, AlertCondition.STATUS_DOWN, IncidentSeverity.MAJOR);

    statusChanged(serviceId, ServiceStatus.HEALTHY, ServiceStatus.DOWN);
    statusChanged(orderId, ServiceStatus.HEALTHY, ServiceStatus.DOWN);

    List<JsonNode> incidents = incidents();
    assertThat(incidents).hasSize(1);
    assertThat(incidents.get(0).get("title").asText()).isEqualTo("payment-service is DOWN");
    assertThat(ids(incidents.get(0).get("affectedServiceIds")))
        .containsExactlyInAnyOrder(serviceId.toString(), orderId.toString());
    assertThat(eventMessages(incidents.get(0)))
        .contains("order-service changed status from HEALTHY to DOWN (depends on payment-service)");
  }

  @Test
  void rootCauseJoinsADependentsIncidentWhenItReportsSecond() throws Exception {
    UUID orderId = createService("order-service");
    UUID gatewayId = createService("api-gateway");
    dependsOn(gatewayId, orderId);
    dependsOn(orderId, serviceId);
    addRule(serviceId, AlertCondition.STATUS_DOWN, IncidentSeverity.CRITICAL);
    addRule(gatewayId, AlertCondition.STATUS_DOWN, IncidentSeverity.MINOR);

    // The gateway notices first; payment-service is two hops down its dependency chain.
    statusChanged(gatewayId, ServiceStatus.HEALTHY, ServiceStatus.DOWN);
    statusChanged(serviceId, ServiceStatus.HEALTHY, ServiceStatus.DOWN);

    List<JsonNode> incidents = incidents();
    assertThat(incidents).hasSize(1);
    assertThat(incidents.get(0).get("severity").asText()).isEqualTo("CRITICAL");
    assertThat(incidents.get(0).get("title").asText()).isEqualTo("payment-service is DOWN");
    assertThat(incidents.get(0).get("triggeredByServiceId").asText()).isEqualTo(serviceId.toString());
    assertThat(eventMessages(incidents.get(0)))
        .contains(
            "payment-service changed status from HEALTHY to DOWN (api-gateway depends on it)",
            "Likely root cause is now payment-service");
  }

  @Test
  void groupedIncidentResolvesOnlyWhenEveryServiceRecovers() throws Exception {
    UUID orderId = createService("order-service");
    dependsOn(orderId, serviceId);
    addRule(serviceId, AlertCondition.STATUS_DOWN, IncidentSeverity.CRITICAL);
    addRule(orderId, AlertCondition.STATUS_DOWN, IncidentSeverity.CRITICAL);
    statusChanged(serviceId, ServiceStatus.HEALTHY, ServiceStatus.DOWN);
    statusChanged(orderId, ServiceStatus.HEALTHY, ServiceStatus.DOWN);

    setStatus(serviceId, ServiceStatus.HEALTHY);
    statusChanged(serviceId, ServiceStatus.DOWN, ServiceStatus.HEALTHY);
    JsonNode partlyRecovered = incidents().get(0);
    assertThat(partlyRecovered.get("status").asText()).isEqualTo("INVESTIGATING");
    assertThat(eventMessages(partlyRecovered))
        .contains("payment-service recovered; still waiting on order-service");

    setStatus(orderId, ServiceStatus.HEALTHY);
    statusChanged(orderId, ServiceStatus.DOWN, ServiceStatus.HEALTHY);
    JsonNode resolved = incidents().get(0);
    assertThat(resolved.get("status").asText()).isEqualTo("RESOLVED");
    assertThat(eventMessages(resolved))
        .contains("Resolved automatically: all affected services recovered");
  }

  @Test
  void unrelatedServicesGetSeparateIncidents() throws Exception {
    UUID userId = createService("user-service");
    addRule(serviceId, AlertCondition.STATUS_DOWN, IncidentSeverity.CRITICAL);
    addRule(userId, AlertCondition.STATUS_DOWN, IncidentSeverity.CRITICAL);

    statusChanged(serviceId, ServiceStatus.HEALTHY, ServiceStatus.DOWN);
    statusChanged(userId, ServiceStatus.HEALTHY, ServiceStatus.DOWN);

    assertThat(incidents()).hasSize(2);
  }

  @Test
  void downOpensOneIncidentAndRepeatsUpdateIt() throws Exception {
    addRule(AlertCondition.STATUS_DOWN, IncidentSeverity.CRITICAL);

    statusChanged(ServiceStatus.HEALTHY, ServiceStatus.DOWN);
    statusChanged(ServiceStatus.DEGRADED, ServiceStatus.DOWN);

    List<JsonNode> incidents = incidents();
    assertThat(incidents).hasSize(1);
    JsonNode incident = incidents.get(0);
    assertThat(incident.get("title").asText()).isEqualTo("payment-service is DOWN");
    assertThat(incident.get("severity").asText()).isEqualTo("CRITICAL");
    assertThat(incident.get("status").asText()).isEqualTo("INVESTIGATING");
    assertThat(incident.get("triggeredByServiceId").asText()).isEqualTo(serviceId.toString());
    assertThat(incident.get("createdById").isNull()).isTrue();
    assertThat(incident.get("affectedServiceIds").get(0).asText()).isEqualTo(serviceId.toString());
    assertThat(eventTypes(incident)).containsExactlyInAnyOrder("CREATED", "ALERT_TRIGGERED");
  }

  @Test
  void severityOnlyRises() throws Exception {
    addRule(AlertCondition.STATUS_DEGRADED, IncidentSeverity.MINOR);
    addRule(AlertCondition.STATUS_DOWN, IncidentSeverity.CRITICAL);

    statusChanged(ServiceStatus.HEALTHY, ServiceStatus.DEGRADED);
    assertThat(incidents().get(0).get("severity").asText()).isEqualTo("MINOR");

    statusChanged(ServiceStatus.DEGRADED, ServiceStatus.DOWN);
    JsonNode raised = incidents().get(0);
    assertThat(raised.get("severity").asText()).isEqualTo("CRITICAL");
    assertThat(raised.get("title").asText()).isEqualTo("payment-service is DOWN");
    assertThat(eventTypes(raised)).contains("SEVERITY_CHANGED");

    statusChanged(ServiceStatus.DOWN, ServiceStatus.DEGRADED);
    assertThat(incidents()).hasSize(1);
    assertThat(incidents().get(0).get("severity").asText()).isEqualTo("CRITICAL");
  }

  @Test
  void recoveryResolvesAndTheNextOutageOpensANewIncident() throws Exception {
    addRule(AlertCondition.STATUS_DOWN, IncidentSeverity.MAJOR);

    statusChanged(ServiceStatus.HEALTHY, ServiceStatus.DOWN);
    statusChanged(ServiceStatus.DOWN, ServiceStatus.HEALTHY);

    JsonNode resolved = incidents().get(0);
    assertThat(resolved.get("status").asText()).isEqualTo("RESOLVED");
    assertThat(resolved.get("resolvedAt").isNull()).isFalse();

    statusChanged(ServiceStatus.HEALTHY, ServiceStatus.DOWN);
    assertThat(incidents()).hasSize(2);
  }

  @Test
  void rulesWithoutSeverityDoNotOpenIncidents() throws Exception {
    // Unroutable webhook: delivery fails fast and is only logged.
    call(
        post(rulesPath())
            .content(json(new CreateAlertRuleRequest(AlertCondition.STATUS_DOWN, "http://localhost:1/hook", null, null, null, null))));

    statusChanged(ServiceStatus.HEALTHY, ServiceStatus.DOWN);

    assertThat(incidents()).isEmpty();
  }

  @Test
  void maintenanceSuppressesOpeningButNotRecovery() throws Exception {
    addRule(AlertCondition.STATUS_DOWN, IncidentSeverity.CRITICAL);
    statusChanged(ServiceStatus.HEALTHY, ServiceStatus.DOWN);
    assertThat(incidents()).hasSize(1);

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

    statusChanged(ServiceStatus.DOWN, ServiceStatus.HEALTHY);
    assertThat(incidents().get(0).get("status").asText()).isEqualTo("RESOLVED");

    statusChanged(ServiceStatus.HEALTHY, ServiceStatus.DOWN);
    assertThat(incidents()).hasSize(1);
  }

  private void addRule(AlertCondition condition, IncidentSeverity severity) throws Exception {
    addRule(serviceId, condition, severity);
  }

  private void addRule(UUID service, AlertCondition condition, IncidentSeverity severity) throws Exception {
    call(post(rulesPath(service)).content(json(new CreateAlertRuleRequest(condition, null, null, severity, null, null))));
  }

  private String rulesPath() {
    return rulesPath(serviceId);
  }

  private String rulesPath(UUID service) {
    return "/api/v1/organizations/" + orgId + "/services/" + service + "/alert-rules";
  }

  private UUID createService(String name) throws Exception {
    return UUID.fromString(
        call(
                post("/api/v1/organizations/" + orgId + "/services")
                    .content(json(new CreateServiceRequest(name, null, null, null, null))))
            .get("id")
            .asText());
  }

  private void dependsOn(UUID service, UUID dependency) throws Exception {
    call(
        post("/api/v1/organizations/" + orgId + "/services/" + service + "/dependencies")
            .content(json(new AddDependencyRequest(dependency))));
  }

  /** Recovery reads other affected services' stored status, which events alone don't change. */
  private void setStatus(UUID service, ServiceStatus status) {
    var entity = serviceRepository.findById(service).orElseThrow();
    entity.setStatus(status);
    serviceRepository.save(entity);
  }

  private void statusChanged(ServiceStatus from, ServiceStatus to) throws Exception {
    statusChanged(serviceId, from, to);
  }

  private void statusChanged(UUID service, ServiceStatus from, ServiceStatus to) throws Exception {
    consumer.consumeStatusChangedEvent(
        json(new ServiceStatusChangedEvent(service, UUID.fromString(orgId), from, to, OffsetDateTime.now())));
  }

  private static List<String> ids(JsonNode array) {
    List<String> result = new ArrayList<>();
    array.forEach(id -> result.add(id.asText()));
    return result;
  }

  private List<String> eventMessages(JsonNode incident) throws Exception {
    List<String> messages = new ArrayList<>();
    call(get("/api/v1/organizations/" + orgId + "/incidents/" + incident.get("id").asText() + "/events"))
        .forEach(e -> messages.add(e.get("message").asText()));
    return messages;
  }

  private List<JsonNode> incidents() throws Exception {
    List<JsonNode> result = new ArrayList<>();
    call(get("/api/v1/organizations/" + orgId + "/incidents")).forEach(result::add);
    return result;
  }

  private List<String> eventTypes(JsonNode incident) throws Exception {
    List<String> types = new ArrayList<>();
    call(get("/api/v1/organizations/" + orgId + "/incidents/" + incident.get("id").asText() + "/events"))
        .forEach(e -> types.add(e.get("eventType").asText()));
    return types;
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
