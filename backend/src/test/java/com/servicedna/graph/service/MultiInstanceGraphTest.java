package com.servicedna.graph.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.billing.domain.PlanType;
import com.servicedna.billing.repository.SubscriptionRepository;
import com.servicedna.graph.domain.CallKey;
import com.servicedna.graph.domain.ObservedCall;
import com.servicedna.graph.domain.Protocol;
import com.servicedna.graph.domain.TargetKind;
import com.servicedna.graph.repository.ObservedCallRepository;
import com.servicedna.graph.service.ObservedCallCollector.Inbound;
import com.servicedna.graph.service.ObservedCallCollector.Outbound;
import com.servicedna.ingestion.service.ServiceDiscoveryListener;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two backend instances sharing Redis and the database: instance A is the application's own beans,
 * instance B a second collector, pending-call store and aggregator built on the same Redis and DB.
 */
@SpringBootTest(properties = {"graph.flush-interval-ms=3600000", "graph.pairing-sweep-ms=3600000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MultiInstanceGraphTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SubscriptionRepository subscriptions;
  @Autowired private StringRedisTemplate redis;
  @Autowired private ServiceDiscoveryListener serviceDiscovery;
  @Autowired private HostServiceResolver resolver;
  @Autowired private ObservedCallRepository observedCalls;
  @Autowired private TransactionTemplate transactions;
  @Autowired private ServiceEdgeTracker edges;

  private UUID orgId;
  private String prefix;
  private final ExecutorService pool = Executors.newFixedThreadPool(4);

  @BeforeEach
  void setUp() throws Exception {
    String token = json(post("/api/v1/auth/register").content(objectMapper.writeValueAsString(
        new RegisterRequest("multi-" + UUID.randomUUID() + "@example.com", "password123")))).get("token").asText();
    orgId = UUID.fromString(json(post("/api/v1/organizations").header("Authorization", "Bearer " + token)
        .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Multi Org")))).get("id").asText());
    var subscription = subscriptions.findByOrganizationId(orgId).orElseThrow();
    subscription.setPlanType(PlanType.PRO);
    subscriptions.save(subscription);
    // Its own key space: other test contexts' sweepers share this Redis.
    prefix = "sdna:{pairing-test-" + UUID.randomUUID() + "}:";
  }

  @AfterEach
  void tearDown() {
    pool.shutdownNow();
    Set<String> keys = redis.keys(prefix + "*");
    if (keys != null && !keys.isEmpty()) {
      redis.delete(keys);
    }
  }

  private RedisPendingCalls store() {
    return new RedisPendingCalls(redis, objectMapper, Duration.ofSeconds(30), prefix);
  }

  private record Instance(ObservedCallCollector collector, CallAggregator aggregator) {}

  private Instance instance() {
    CallAggregator aggregator = new CallAggregator(observedCalls, transactions, edges);
    return new Instance(new ObservedCallCollector(serviceDiscovery, aggregator, resolver, store(), 30_000), aggregator);
  }

  @Test
  void theHalvesOfACallPairWhenTheyReachDifferentInstances() {
    Instance a = instance();
    Instance b = instance();
    ByteString trace = id(16);
    Span entry = span(trace, id(8), ByteString.EMPTY, Span.SpanKind.SPAN_KIND_SERVER, "GET /api/orders", attr("http.route", "/api/orders"), attr("http.request.method", "GET"));
    Span client = span(trace, id(8), entry.getSpanId(), Span.SpanKind.SPAN_KIND_CLIENT, "POST", attr("http.request.method", "POST"));
    Span server = span(trace, id(8), client.getSpanId(), Span.SpanKind.SPAN_KIND_SERVER, "POST /orders", attr("http.route", "/orders"), attr("http.request.method", "POST"));

    a.collector().collect(orgId, resource("gateway", entry, client)); // the caller's half on instance A
    b.collector().collect(orgId, resource("orders", server)); // the callee's half on instance B
    a.aggregator().flush();
    b.aggregator().flush();

    List<ObservedCall> rows = rows();
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getTargetKind()).isEqualTo(TargetKind.SERVICE);
    assertThat(rows.get(0).getSourceOperation()).isEqualTo("GET /api/orders");
    assertThat(rows.get(0).getTargetOperation()).isEqualTo("POST /orders");
    assertThat(rows.get(0).getCalls()).isEqualTo(1);
  }

  @Test
  void halvesArrivingAtOnceOnTwoInstancesAlwaysPairExactlyOnce() throws Exception {
    RedisPendingCalls a = store();
    RedisPendingCalls b = store();
    int calls = 500;
    AtomicInteger paired = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    Instant expires = Instant.now().plusSeconds(30);
    Future<?> callers = pool.submit(() -> {
      start.await();
      for (int i = 0; i < calls; i++) {
        if (a.pairOutbound("call-" + i, outbound(expires)).isPresent()) {
          paired.incrementAndGet();
        }
      }
      return null;
    });
    Future<?> callees = pool.submit(() -> {
      start.await();
      for (int i = 0; i < calls; i++) {
        if (b.pairInbound("call-" + i, new Inbound(UUID.randomUUID(), "POST /orders", false, expires)).isPresent()) {
          paired.incrementAndGet();
        }
      }
      return null;
    });
    start.countDown();
    callers.get();
    callees.get();

    assertThat(paired.get()).isEqualTo(calls); // each call paired once: never twice, never missed
    assertThat(redis.keys(prefix + "*")).isEmpty(); // and nothing left waiting
  }

  @Test
  void anExpiredCallIsClaimedByExactlyOneInstance() throws Exception {
    RedisPendingCalls a = store();
    RedisPendingCalls b = store();
    Instant past = Instant.now().minusSeconds(1);
    for (int i = 0; i < 300; i++) {
      a.pairOutbound("lonely-" + i, outbound(past));
    }
    Future<List<Outbound>> fromA = pool.submit(() -> a.takeExpired(Instant.now()));
    Future<List<Outbound>> fromB = pool.submit(() -> b.takeExpired(Instant.now()));
    List<Outbound> claimed = new ArrayList<>(fromA.get());
    claimed.addAll(fromB.get());

    assertThat(claimed).hasSize(300);
    assertThat(claimed.get(0).attributes()).extracting(KeyValue::getKey).containsExactly("server.address");
    assertThat(claimed.get(0).protocol()).isEqualTo(Protocol.HTTP);
    assertThat(a.takeExpired(Instant.now())).isEmpty();
  }

  @Test
  void twoInstancesFlushingTheSameBucketLoseNoCounts() throws Exception {
    Instance a = instance();
    Instance b = instance();
    UUID gateway = register("gateway");
    UUID orders = register("orders");
    CallKey key = new CallKey(gateway, "GET /api/orders", orders, TargetKind.SERVICE, orders.toString(), "POST /orders", Protocol.HTTP);
    Instant minute = Instant.now();

    for (int round = 0; round < 5; round++) {
      for (int i = 0; i < 20; i++) {
        a.aggregator().record(orgId, minute, key, 5, false);
        b.aggregator().record(orgId, minute, key, 5, i % 2 == 0);
      }
      CountDownLatch start = new CountDownLatch(1);
      Future<?> fa = pool.submit(() -> {
        start.await();
        a.aggregator().flush();
        return null;
      });
      Future<?> fb = pool.submit(() -> {
        start.await();
        b.aggregator().flush();
        return null;
      });
      start.countDown();
      fa.get();
      fb.get();
    }

    List<ObservedCall> rows = rows();
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getCalls()).isEqualTo(200);
    assertThat(rows.get(0).getErrors()).isEqualTo(50);
  }

  private UUID register(String service) {
    return serviceDiscovery.register(orgId, ServiceDiscoveryListener.identityOf(resource(service).getResource().getAttributesList())).orElseThrow();
  }

  private List<ObservedCall> rows() {
    return observedCalls.findAll().stream().filter(c -> c.getOrganizationId().equals(orgId)).toList();
  }

  private static Outbound outbound(Instant expires) {
    return new Outbound(UUID.randomUUID(), UUID.randomUUID(), "dev", "GET /api/orders", Protocol.HTTP,
        List.of(attr("server.address", "api.stripe.com")), "POST", Instant.now(), 12, false, expires);
  }

  private static ResourceSpans resource(String service, Span... spans) {
    return ResourceSpans.newBuilder()
        .setResource(Resource.newBuilder().addAttributes(attr("service.name", service)))
        .addScopeSpans(ScopeSpans.newBuilder().addAllSpans(List.of(spans)))
        .build();
  }

  private static Span span(ByteString trace, ByteString spanId, ByteString parent, Span.SpanKind kind, String name, KeyValue... attributes) {
    long start = Instant.now().toEpochMilli() * 1_000_000;
    return Span.newBuilder().setTraceId(trace).setSpanId(spanId).setParentSpanId(parent).setKind(kind).setName(name)
        .setStartTimeUnixNano(start).setEndTimeUnixNano(start + 10_000_000).addAllAttributes(List.of(attributes)).build();
  }

  private static KeyValue attr(String key, String value) {
    return KeyValue.newBuilder().setKey(key).setValue(AnyValue.newBuilder().setStringValue(value)).build();
  }

  private static ByteString id(int bytes) {
    byte[] b = new byte[bytes];
    ThreadLocalRandom.current().nextBytes(b);
    return ByteString.copyFrom(b);
  }

  private JsonNode json(MockHttpServletRequestBuilder request) throws Exception {
    return objectMapper.readTree(mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse().getContentAsString());
  }
}
