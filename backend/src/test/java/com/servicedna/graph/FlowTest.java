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

@SpringBootTest(properties = "graph.flush-interval-ms=3600000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlowTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CallAggregator aggregator;
  @Autowired private SubscriptionRepository subscriptionRepository;

  private String token;
  private String orgId;
  private UUID gateway, orders, payments, products;

  @BeforeEach
  void setUp() throws Exception {
    token = call(post("/api/v1/auth/register").content(json(new RegisterRequest("flow-" + UUID.randomUUID() + "@example.com", "password123")))).get("token").asText();
    orgId = call(post("/api/v1/organizations").content(json(new CreateOrganizationRequest("Flow Org")))).get("id").asText();
    var subscription = subscriptionRepository.findByOrganizationId(UUID.fromString(orgId)).orElseThrow();
    subscription.setPlanType(PlanType.PRO);
    subscriptionRepository.save(subscription);
    gateway = service("gateway");
    orders = service("orders");
    payments = service("payments");
    products = service("products");

    record(gateway, "POST /api/orders", orders, "POST /orders", 40, 3);
    record(orders, "POST /orders", payments, "POST /charge", 12, 3);
    record(payments, "POST /charge", null, "SELECT", 2, 3);
    record(gateway, "GET /api/products", products, "GET /products", 8, 5);
    aggregator.flush();
  }

  @Test
  void entryPointsAreOperationsNothingInstrumentedCalls() throws Exception {
    JsonNode entries = call(get("/api/v1/organizations/" + orgId + "/flows/entry-points"));

    List<String> names = new ArrayList<>();
    entries.forEach(e -> names.add(e.get("nodeName").asText() + " " + e.get("operation").asText()));
    assertThat(names).containsExactly("gateway GET /api/products", "gateway POST /api/orders");
  }

  @Test
  void anEntryShowsEverythingItTriggersDownstream() throws Exception {
    JsonNode flow = call(get("/api/v1/organizations/" + orgId + "/flows")
        .param("entryNode", gateway.toString())
        .param("entryOperation", "POST /api/orders"));

    List<String> operations = new ArrayList<>();
    flow.get("operations").forEach(o -> operations.add(o.get("nodeName").asText() + " " + o.get("operation").asText()));
    assertThat(operations).containsExactlyInAnyOrder(
        "gateway POST /api/orders", "orders POST /orders", "payments POST /charge", "postgresql/shop SELECT");
    assertThat(flow.get("calls")).hasSize(3);

    JsonNode first = null;
    for (JsonNode c : flow.get("calls")) {
      if (c.get("target").asText().equals(orders + "|POST /orders")) {
        first = c;
      }
    }
    assertThat(first).isNotNull();
    assertThat(first.get("protocol").asText()).isEqualTo("HTTP");
    assertThat(first.get("calls").asLong()).isEqualTo(3);
    assertThat(first.get("p50Ms").asLong()).isEqualTo(40);
    assertThat(first.get("p95Ms").asLong()).isEqualTo(40);
  }

  @Test
  void withoutAnEntryTheWholeMapIsReturned() throws Exception {
    assertThat(call(get("/api/v1/organizations/" + orgId + "/flows")).get("calls")).hasSize(4);
  }

  private void record(UUID source, String sourceOp, UUID target, String targetOp, long ms, int times) {
    CallKey key =
        target != null
            ? new CallKey(source, sourceOp, target, TargetKind.SERVICE, target.toString(), targetOp, Protocol.HTTP)
            : new CallKey(source, sourceOp, null, TargetKind.DATABASE, "postgresql/shop", targetOp, Protocol.DATABASE);
    for (int i = 0; i < times; i++) {
      aggregator.record(UUID.fromString(orgId), Instant.now(), key, ms, false);
    }
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
    return objectMapper.readTree(mockMvc.perform(request).andReturn().getResponse().getContentAsString());
  }
}
