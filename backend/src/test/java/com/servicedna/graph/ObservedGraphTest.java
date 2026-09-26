package com.servicedna.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.billing.domain.PlanType;
import com.servicedna.billing.repository.SubscriptionRepository;
import com.servicedna.graph.domain.ObservedCall;
import com.servicedna.graph.repository.ObservedCallRepository;
import com.servicedna.graph.service.CallAggregator;
import com.servicedna.graph.service.GraphService;
import com.servicedna.graph.service.ObservedCallCollector;
import com.servicedna.ingestion.dto.CreateIngestionKeyRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.service.dto.AddDependencyRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {"graph.pairing-window-ms=50", "graph.flush-interval-ms=3600000", "graph.pairing-sweep-ms=3600000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObservedGraphTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CallAggregator aggregator;
  @Autowired private ObservedCallCollector collector;
  @Autowired private ObservedCallRepository observedCallRepository;
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private com.servicedna.service.service.DependencyGraphService dependencyGraphService;

  private String token;
  private String orgId;
  private String key;

  @BeforeEach
  void setUp() throws Exception {
    token =
        call(post("/api/v1/auth/register").content(json(new RegisterRequest("graph-" + UUID.randomUUID() + "@example.com", "password123"))))
            .get("token")
            .asText();
    orgId = call(post("/api/v1/organizations").content(json(new CreateOrganizationRequest("Graph Org")))).get("id").asText();
    var subscription = subscriptionRepository.findByOrganizationId(UUID.fromString(orgId)).orElseThrow();
    subscription.setPlanType(PlanType.PRO);
    subscriptionRepository.save(subscription);
    key = call(post("/api/v1/organizations/" + orgId + "/ingestion-keys").content(json(new CreateIngestionKeyRequest("t")))).get("key").asText();
  }

  @Test
  void pairsAClientSpanWithTheServerSpanItCaused() throws Exception {
    ByteString trace = id(16);
    Span entry = span(trace, id(8), ByteString.EMPTY, Span.SpanKind.SPAN_KIND_SERVER, "GET /api/orders", 40, attr("http.request.method", "GET"), attr("http.route", "/api/orders"));
    Span client = span(trace, id(8), entry.getSpanId(), Span.SpanKind.SPAN_KIND_CLIENT, "POST", 25, attr("http.request.method", "POST"));
    Span server = span(trace, id(8), client.getSpanId(), Span.SpanKind.SPAN_KIND_SERVER, "POST /orders", 20, attr("http.request.method", "POST"), attr("http.route", "/orders"));

    export("gateway", entry, client);
    export("orders", server);
    aggregator.flush();

    JsonNode edge = edge("gateway", "orders");
    assertThat(edge.get("observed").asBoolean()).isTrue();
    assertThat(edge.get("declared").asBoolean()).isFalse();
    assertThat(edge.get("calls").asLong()).isEqualTo(1);
    assertThat(edge.get("protocols").get(0).asText()).isEqualTo("HTTP");
    assertThat(edge.get("p95Ms").asLong()).isEqualTo(25); // ≤50 ms bucket, capped at the slowest call seen

    ObservedCall row = observedCallRepository.findAll().stream()
        .filter(c -> c.getOrganizationId().toString().equals(orgId)).findFirst().orElseThrow();
    assertThat(row.getSourceOperation()).isEqualTo("GET /api/orders");
    assertThat(row.getTargetOperation()).isEqualTo("POST /orders");
  }

  @Test
  void pairsWhicheverHalfArrivesFirst() throws Exception {
    ByteString trace = id(16);
    Span client = span(trace, id(8), ByteString.EMPTY, Span.SpanKind.SPAN_KIND_CLIENT, "GET", 10, attr("http.request.method", "GET"));
    Span server = span(trace, id(8), client.getSpanId(), Span.SpanKind.SPAN_KIND_SERVER, "GET /stock", 5, attr("http.request.method", "GET"), attr("http.route", "/stock"));

    export("inventory", server);
    export("products", client);
    aggregator.flush();

    assertThat(edge("products", "inventory").get("calls").asLong()).isEqualTo(1);
  }

  @Test
  void databaseCallsBecomeDatabaseNodes() throws Exception {
    ByteString trace = id(16);
    Span query = span(trace, id(8), ByteString.EMPTY, Span.SpanKind.SPAN_KIND_CLIENT, "SELECT orders", 3,
        attr("db.system.name", "postgresql"), attr("db.namespace", "shop"), attr("db.operation.name", "SELECT"));

    export("orders", query);
    aggregator.flush();

    JsonNode graph = graph();
    assertThat(node(graph, "DATABASE:postgresql/shop").get("kind").asText()).isEqualTo("DATABASE");
    assertThat(edge(graph, "orders", "DATABASE:postgresql/shop").get("protocols").get(0).asText()).isEqualTo("DATABASE");
  }

  @Test
  void unansweredCallsBecomeExternalNodesAfterThePairingWindow() throws Exception {
    Span stripe = span(id(16), id(8), ByteString.EMPTY, Span.SpanKind.SPAN_KIND_CLIENT, "POST", 300,
        attr("http.request.method", "POST"), attr("server.address", "api.stripe.com"));

    export("payments", stripe);
    Thread.sleep(80);
    collector.expireUnpaired();
    aggregator.flush();

    assertThat(edge(graph(), "payments", "EXTERNAL:api.stripe.com").get("calls").asLong()).isEqualTo(1);
  }

  @Test
  void messagingConsumersPairThroughLinks() throws Exception {
    ByteString trace = id(16);
    Span produce = span(trace, id(8), ByteString.EMPTY, Span.SpanKind.SPAN_KIND_PRODUCER, "order.created publish", 2,
        attr("messaging.system", "kafka"), attr("messaging.destination.name", "order.created"));
    Span consume = span(id(16), id(8), ByteString.EMPTY, Span.SpanKind.SPAN_KIND_CONSUMER, "order.created process", 8,
            attr("messaging.system", "kafka"), attr("messaging.destination.name", "order.created"))
        .toBuilder()
        .addLinks(Span.Link.newBuilder().setTraceId(trace).setSpanId(produce.getSpanId()))
        .build();

    export("orders", produce);
    export("notifications", consume);
    aggregator.flush();

    JsonNode edge = edge("orders", "notifications");
    assertThat(edge.get("protocols").get(0).asText()).isEqualTo("MESSAGING");
    ObservedCall row = observedCallRepository.findAll().stream()
        .filter(c -> c.getOrganizationId().toString().equals(orgId)).findFirst().orElseThrow();
    assertThat(row.getTargetOperation()).isEqualTo("consume order.created");
  }

  @Test
  void flagsDeclaredEdgesThatTrafficNeverShowed() throws Exception {
    export("api", span(id(16), id(8), ByteString.EMPTY, Span.SpanKind.SPAN_KIND_SERVER, "GET /", 1));
    export("legacy", span(id(16), id(8), ByteString.EMPTY, Span.SpanKind.SPAN_KIND_SERVER, "GET /", 1));
    JsonNode graph = graph();
    String api = serviceId(graph, "api");
    call(post("/api/v1/organizations/" + orgId + "/services/" + api + "/dependencies")
        .content(json(new AddDependencyRequest(UUID.fromString(serviceId(graph, "legacy"))))));

    JsonNode edge = edge(graph(), "api", "legacy");
    assertThat(edge.get("declared").asBoolean()).isTrue();
    assertThat(edge.get("observed").asBoolean()).isFalse();
  }

  @Test
  void unansweredCallsToAKnownServiceHostCountAsCallsToThatService() throws Exception {
    export("product-service", span(id(16), id(8), ByteString.EMPTY, Span.SpanKind.SPAN_KIND_SERVER, "GET /", 1));
    Span healthCheck = span(id(16), id(8), ByteString.EMPTY, Span.SpanKind.SPAN_KIND_CLIENT, "GET", 4,
        attr("http.request.method", "GET"), attr("server.address", "product-service"), attr("url.full", "http://product-service:4002/users/u-17/orders"));

    export("order-service", healthCheck);
    Thread.sleep(80);
    collector.expireUnpaired();
    aggregator.flush();

    assertThat(edge("order-service", "product-service").get("calls").asLong()).isEqualTo(1);
    ObservedCall row = observedCallRepository.findAll().stream()
        .filter(c -> c.getOrganizationId().toString().equals(orgId)).findFirst().orElseThrow();
    assertThat(row.getTargetOperation()).isEqualTo("GET /users/{id}/orders");
  }

  @Test
  void pathsKeepTheirShapeWithoutIds() {
    assertThat(ObservedCallCollector.normalizePath("/orders/42")).isEqualTo("/orders/{id}");
    assertThat(ObservedCallCollector.normalizePath("/health")).isEqualTo("/health");
    assertThat(ObservedCallCollector.normalizePath("/v2/users/u-1/")).isEqualTo("/{id}/users/{id}/");
  }

  @Test
  void observedCallsCountAsDependenciesForIncidentGrouping() throws Exception {
    ByteString trace = id(16);
    Span client = span(trace, id(8), ByteString.EMPTY, Span.SpanKind.SPAN_KIND_CLIENT, "POST", 10, attr("http.request.method", "POST"));
    Span server = span(trace, id(8), client.getSpanId(), Span.SpanKind.SPAN_KIND_SERVER, "POST /charge", 5, attr("http.request.method", "POST"), attr("http.route", "/charge"));
    export("checkout", client);
    export("payments", server);
    aggregator.flush();

    JsonNode graph = graph();
    var relations = dependencyGraphService.relationsOf(UUID.fromString(orgId), UUID.fromString(serviceId(graph, "checkout")));
    assertThat(relations.dependencies()).containsExactly(UUID.fromString(serviceId(graph, "payments")));
  }

  @Test
  void percentileComesFromTheHistogram() {
    long[] buckets = {90, 5, 0, 4, 0, 0, 0, 0, 0, 1};
    assertThat(GraphService.percentileMs(buckets, 100, 12000, 0.95)).isEqualTo(50);
    assertThat(GraphService.percentileMs(buckets, 100, 12000, 0.99)).isEqualTo(250);
    assertThat(GraphService.percentileMs(buckets, 100, 12000, 1.0)).isEqualTo(12000);
    assertThat(GraphService.percentileMs(new long[10], 0, 0, 0.95)).isNull();
  }

  // --- helpers --------------------------------------------------------------------------------

  private void export(String service, Span... spans) throws Exception {
    byte[] body =
        ExportTraceServiceRequest.newBuilder()
            .addResourceSpans(
                ResourceSpans.newBuilder()
                    .setResource(Resource.newBuilder().addAttributes(attr("service.name", service)))
                    .addScopeSpans(ScopeSpans.newBuilder().addAllSpans(List.of(spans))))
            .build()
            .toByteArray();
    mockMvc.perform(
        post("/api/v1/otlp/v1/traces").header("x-servicedna-key", key).contentType("application/x-protobuf").content(body));
  }

  private static Span span(ByteString trace, ByteString spanId, ByteString parent, Span.SpanKind kind, String name, long durationMs, KeyValue... attributes) {
    long start = Instant.now().toEpochMilli() * 1_000_000;
    return Span.newBuilder()
        .setTraceId(trace)
        .setSpanId(spanId)
        .setParentSpanId(parent)
        .setKind(kind)
        .setName(name)
        .setStartTimeUnixNano(start)
        .setEndTimeUnixNano(start + durationMs * 1_000_000)
        .addAllAttributes(List.of(attributes))
        .build();
  }

  private static KeyValue attr(String k, String v) {
    return KeyValue.newBuilder().setKey(k).setValue(AnyValue.newBuilder().setStringValue(v)).build();
  }

  private static ByteString id(int bytes) {
    byte[] b = new byte[bytes];
    ThreadLocalRandom.current().nextBytes(b);
    return ByteString.copyFrom(b);
  }

  private JsonNode graph() throws Exception {
    return call(get("/api/v1/organizations/" + orgId + "/graph"));
  }

  private JsonNode edge(String sourceName, String targetName) throws Exception {
    JsonNode graph = graph();
    return edge(graph, sourceName, targetName);
  }

  private JsonNode edge(JsonNode graph, String sourceName, String target) {
    String source = serviceId(graph, sourceName);
    String targetId = target.contains(":") ? target : serviceId(graph, target);
    for (JsonNode e : graph.get("edges")) {
      if (e.get("source").asText().equals(source) && e.get("target").asText().equals(targetId)) {
        return e;
      }
    }
    throw new AssertionError("no edge " + sourceName + " -> " + target + " in " + graph);
  }

  private static JsonNode node(JsonNode graph, String id) {
    for (JsonNode n : graph.get("nodes")) {
      if (n.get("id").asText().equals(id)) {
        return n;
      }
    }
    throw new AssertionError("no node " + id);
  }

  private static String serviceId(JsonNode graph, String name) {
    List<String> names = new ArrayList<>();
    for (JsonNode n : graph.get("nodes")) {
      if (n.get("name").asText().equals(name) && n.get("kind").asText().equals("SERVICE")) {
        return n.get("id").asText();
      }
      names.add(n.get("name").asText());
    }
    throw new AssertionError("no service " + name + " in " + names);
  }

  private String json(Object body) throws Exception {
    return objectMapper.writeValueAsString(body);
  }

  private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
    request.contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token);
    return objectMapper.readTree(mockMvc.perform(request).andReturn().getResponse().getContentAsString());
  }
}
