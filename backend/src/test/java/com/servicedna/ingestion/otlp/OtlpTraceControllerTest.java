package com.servicedna.ingestion.otlp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.ingestion.dto.CreateIngestionKeyRequest;
import com.servicedna.ingestion.event.TraceBatchReceivedEvent;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class OtlpTraceControllerTest {

  /** Stands in for Tempo's OTLP/HTTP receiver. */
  private static final HttpServer traceStore;
  private static final List<String> tenants = new CopyOnWriteArrayList<>();
  private static final AtomicInteger storeStatus = new AtomicInteger(200);

  static {
    try {
      traceStore = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
    traceStore.createContext(
        "/v1/traces",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          tenants.add(exchange.getRequestHeaders().getFirst("X-Scope-OrgID"));
          exchange.sendResponseHeaders(storeStatus.get(), -1);
          exchange.close();
        });
    traceStore.start();
  }

  @DynamicPropertySource
  static void traceStoreUrl(DynamicPropertyRegistry registry) {
    registry.add("trace-store.otlp-url", () -> "http://127.0.0.1:" + traceStore.getAddress().getPort());
  }

  @AfterAll
  static void stopTraceStore() {
    traceStore.stop(0);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ApplicationEvents events;

  private String orgId;
  private String ingestionKey;

  @BeforeEach
  void setUp() throws Exception {
    tenants.clear();
    storeStatus.set(200);
    String token =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(
                                    new RegisterRequest("otlp-" + UUID.randomUUID() + "@example.com", "password123"))))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("token")
            .asText();
    orgId = field(post("/api/v1/organizations").content(objectMapper.writeValueAsString(new CreateOrganizationRequest("OTLP Org"))), token, "id");
    ingestionKey =
        field(
            post("/api/v1/organizations/" + orgId + "/ingestion-keys")
                .content(objectMapper.writeValueAsString(new CreateIngestionKeyRequest("test"))),
            token,
            "key");
  }

  @Test
  void acceptsSpansStoresThemPerTenantAndPublishesTheBatch() throws Exception {
    mockMvc
        .perform(export(batch("checkout-service", 3)).header("x-servicedna-key", ingestionKey))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/x-protobuf"));

    assertThat(tenants).containsExactly(orgId);
    List<TraceBatchReceivedEvent> received = events.stream(TraceBatchReceivedEvent.class).toList();
    assertThat(received).hasSize(1);
    assertThat(received.get(0).organizationId().toString()).isEqualTo(orgId);
    assertThat(received.get(0).request().getResourceSpans(0).getScopeSpans(0).getSpansCount()).isEqualTo(3);
  }

  @Test
  void acceptsGzippedBatches() throws Exception {
    var buffer = new java.io.ByteArrayOutputStream();
    try (var gzip = new java.util.zip.GZIPOutputStream(buffer)) {
      gzip.write(batch("checkout-service", 2));
    }
    mockMvc
        .perform(
            export(buffer.toByteArray())
                .header("x-servicedna-key", ingestionKey)
                .header("Content-Encoding", "gzip"))
        .andExpect(status().isOk());
    assertThat(events.stream(TraceBatchReceivedEvent.class).findFirst().orElseThrow()
            .request().getResourceSpans(0).getScopeSpans(0).getSpansCount())
        .isEqualTo(2);
  }

  @Test
  void acceptsTheKeyAsABearerToken() throws Exception {
    mockMvc
        .perform(export(batch("checkout-service", 1)).header("Authorization", "Bearer " + ingestionKey))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsMissingOrUnknownKeys() throws Exception {
    mockMvc
        .perform(export(batch("checkout-service", 1)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("INVALID_INGESTION_KEY"));
    mockMvc
        .perform(export(batch("checkout-service", 1)).header("x-servicedna-key", "sdna_nope"))
        .andExpect(status().isUnauthorized());
    assertThat(tenants).isEmpty();
  }

  @Test
  void rejectsBodiesThatAreNotOtlp() throws Exception {
    mockMvc
        .perform(export(new byte[] {(byte) 0xff, 0x01, 0x02}).header("x-servicedna-key", ingestionKey))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_OTLP"));
    mockMvc
        .perform(
            post("/api/v1/otlp/v1/traces")
                .header("x-servicedna-key", ingestionKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void asksTheClientToRetryWhenTheTraceStoreIsDown() throws Exception {
    storeStatus.set(503);

    mockMvc
        .perform(export(batch("checkout-service", 1)).header("x-servicedna-key", ingestionKey))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("TRACE_STORE_UNAVAILABLE"));
    assertThat(events.stream(TraceBatchReceivedEvent.class)).isEmpty();
  }

  static byte[] batch(String serviceName, int spans) {
    ScopeSpans.Builder scope = ScopeSpans.newBuilder();
    for (int i = 0; i < spans; i++) {
      scope.addSpans(
          Span.newBuilder()
              .setTraceId(ByteString.copyFrom(new byte[16]))
              .setSpanId(ByteString.copyFrom(new byte[] {0, 0, 0, 0, 0, 0, 0, (byte) (i + 1)}))
              .setName("GET /orders")
              .setKind(Span.SpanKind.SPAN_KIND_SERVER));
    }
    return ExportTraceServiceRequest.newBuilder()
        .addResourceSpans(
            ResourceSpans.newBuilder()
                .setResource(
                    Resource.newBuilder()
                        .addAttributes(
                            KeyValue.newBuilder()
                                .setKey("service.name")
                                .setValue(AnyValue.newBuilder().setStringValue(serviceName))))
                .addScopeSpans(scope))
        .build()
        .toByteArray();
  }

  private MockHttpServletRequestBuilder export(byte[] body) {
    return post("/api/v1/otlp/v1/traces").contentType("application/x-protobuf").content(body);
  }

  private String field(MockHttpServletRequestBuilder request, String token, String name) throws Exception {
    request.contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token);
    return objectMapper.readTree(mockMvc.perform(request).andReturn().getResponse().getContentAsString()).get(name).asText();
  }
}
