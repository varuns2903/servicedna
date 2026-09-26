package com.servicedna.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.billing.domain.PlanType;
import com.servicedna.billing.repository.SubscriptionRepository;
import com.servicedna.graph.domain.CallKey;
import com.servicedna.graph.domain.Protocol;
import com.servicedna.graph.domain.TargetKind;
import com.servicedna.graph.service.CallAggregator;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.service.dto.CreateServiceRequest;
import java.time.Instant;
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

@SpringBootTest(properties = "graph.flush-interval-ms=3600000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UndeclaredDependencyTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CallAggregator aggregator;
  @Autowired private SubscriptionRepository subscriptions;

  private String token;
  private String orgId;
  private UUID gateway, orders, payments;

  @BeforeEach
  void setUp() throws Exception {
    token = call(post("/api/v1/auth/register").content(json(new RegisterRequest("drift-" + UUID.randomUUID() + "@example.com", "password123")))).get("token").asText();
    orgId = call(post("/api/v1/organizations").content(json(new CreateOrganizationRequest("Drift Org")))).get("id").asText();
    var subscription = subscriptions.findByOrganizationId(UUID.fromString(orgId)).orElseThrow();
    subscription.setPlanType(PlanType.PRO);
    subscriptions.save(subscription);
    gateway = service("gateway");
    orders = service("orders");
    payments = service("payments");
    call(post("/api/v1/organizations/" + orgId + "/services/" + gateway + "/dependencies").content("{\"dependsOnServiceId\":\"" + orders + "\"}"));
    call(post("/api/v1/organizations/" + orgId + "/services/" + gateway + "/alert-rules")
        .content("{\"condition\":\"UNDECLARED_DEPENDENCY\",\"incidentSeverity\":\"MINOR\"}"));
  }

  @Test
  void aNewUndeclaredDependencyOpensAnIncidentOnce() throws Exception {
    callFrom(gateway, orders, "POST /orders"); // declared: fine
    aggregator.flush();
    assertThat(incidents()).isEmpty();

    callFrom(gateway, payments, "POST /charge"); // undeclared, first time
    aggregator.flush();
    JsonNode opened = incidents();
    assertThat(opened).hasSize(1);
    assertThat(opened.get(0).get("title").asText()).isEqualTo("gateway calls payments, an undeclared dependency");
    JsonNode timeline = call(get("/api/v1/organizations/" + orgId + "/incidents/" + opened.get(0).get("id").asText() + "/events"));
    assertThat(timeline.toString()).contains("gateway started calling payments (HTTP POST /charge), which isn't a declared dependency.");

    callFrom(gateway, payments, "POST /charge"); // seen before: no new alert
    aggregator.flush();
    assertThat(incidents()).hasSize(1);
    int eventCount = call(get("/api/v1/organizations/" + orgId + "/incidents/" + opened.get(0).get("id").asText() + "/events")).size();
    callFrom(orders, payments, "POST /charge"); // orders has no such rule
    aggregator.flush();
    assertThat(call(get("/api/v1/organizations/" + orgId + "/incidents/" + opened.get(0).get("id").asText() + "/events"))).hasSize(eventCount);
  }

  private JsonNode incidents() throws Exception {
    return call(get("/api/v1/organizations/" + orgId + "/incidents"));
  }

  private void callFrom(UUID source, UUID target, String operation) {
    aggregator.record(UUID.fromString(orgId), Instant.now(),
        new CallKey(source, "", target, TargetKind.SERVICE, target.toString(), operation, Protocol.HTTP), 5, false);
  }

  private UUID service(String name) throws Exception {
    return UUID.fromString(call(post("/api/v1/organizations/" + orgId + "/services").content(json(new CreateServiceRequest(name, null, null, null, null)))).get("id").asText());
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
