package com.servicedna.traces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceQueryTest {

  private static final HttpServer tempo;
  private static final List<String> requests = new CopyOnWriteArrayList<>();

  static {
    try {
      tempo = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
    tempo.createContext("/", exchange -> {
      String uri = URLDecoder.decode(exchange.getRequestURI().toString(), StandardCharsets.UTF_8);
      requests.add(exchange.getRequestHeaders().getFirst("X-Scope-OrgID") + " " + uri);
      String body;
      if (uri.startsWith("/api/search")) {
        body = """
            {"traces":[{"traceID":"4bf92f3577b34da6a3ce929d0e0e4736","rootServiceName":"api-gateway",
              "rootTraceName":"POST /api/orders","startTimeUnixNano":"1760000000000000000","durationMs":42}]}""";
      } else if (uri.startsWith("/api/traces/4bf92f3577b34da6a3ce929d0e0e4736")) {
        body = """
            {"batches":[
              {"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"api-gateway"}}]},
               "scopeSpans":[{"spans":[{"spanId":"AAAAAAAAAAE=","name":"POST /api/orders","kind":"SPAN_KIND_SERVER",
                 "startTimeUnixNano":"1760000000000000000","endTimeUnixNano":"1760000000042000000",
                 "attributes":[{"key":"http.route","value":{"stringValue":"/api/orders"}}],"status":{}}]}]},
              {"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"payment-service"}}]},
               "scopeSpans":[{"spans":[{"spanId":"AAAAAAAAAAI=","parentSpanId":"AAAAAAAAAAE=","name":"POST /charge",
                 "kind":"SPAN_KIND_SERVER","startTimeUnixNano":"1760000000010000000","endTimeUnixNano":"1760000000015500000",
                 "status":{"code":"STATUS_CODE_ERROR","message":"declined"}}]}]}]}""";
      } else {
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
        return;
      }
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    tempo.start();
  }

  @DynamicPropertySource
  static void tempoUrl(DynamicPropertyRegistry registry) {
    registry.add("trace-store.query-url", () -> "http://127.0.0.1:" + tempo.getAddress().getPort());
  }

  @AfterAll
  static void stop() {
    tempo.stop(0);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private String token;
  private String orgId;

  @BeforeEach
  void setUp() throws Exception {
    requests.clear();
    token = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new RegisterRequest("traces-" + UUID.randomUUID() + "@example.com", "password123"))))
        .andReturn().getResponse().getContentAsString()).get("token").asText();
    orgId = objectMapper.readTree(mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Traces Org"))))
        .andReturn().getResponse().getContentAsString()).get("id").asText();
  }

  @Test
  void searchesForCallsBetweenTwoOperationsAsTheOrganizationsTenant() throws Exception {
    mockMvc.perform(get("/api/v1/organizations/" + orgId + "/traces").header("Authorization", "Bearer " + token)
            .param("service", "order-service").param("operation", "POST /orders")
            .param("calleeService", "payment-service").param("calleeOperation", "POST /charge"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].traceId").value("4bf92f3577b34da6a3ce929d0e0e4736"))
        .andExpect(jsonPath("$[0].rootOperation").value("POST /api/orders"))
        .andExpect(jsonPath("$[0].durationMs").value(42));

    assertThat(requests).hasSize(1);
    assertThat(requests.get(0)).startsWith(orgId + " /api/search?q=");
    assertThat(requests.get(0)).contains(
        "{ resource.service.name = \"order-service\" && name = \"POST /orders\" } >> "
            + "{ resource.service.name = \"payment-service\" && name = \"POST /charge\" }");
  }

  @Test
  void returnsATraceAsOrderedSpansWithHexIds() throws Exception {
    JsonNode trace = objectMapper.readTree(mockMvc.perform(get("/api/v1/organizations/" + orgId + "/traces/4bf92f3577b34da6a3ce929d0e0e4736")
        .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

    assertThat(trace.get("durationMs").asLong()).isEqualTo(42);
    JsonNode spans = trace.get("spans");
    assertThat(spans).hasSize(2);
    assertThat(spans.get(0).get("spanId").asText()).isEqualTo("0000000000000001");
    assertThat(spans.get(0).get("attributes").get("http.route").asText()).isEqualTo("/api/orders");
    assertThat(spans.get(1).get("parentSpanId").asText()).isEqualTo("0000000000000001");
    assertThat(spans.get(1).get("service").asText()).isEqualTo("payment-service");
    assertThat(spans.get(1).get("durationMs").asDouble()).isEqualTo(5.5);
    assertThat(spans.get(1).get("error").asBoolean()).isTrue();
    assertThat(spans.get(1).get("statusMessage").asText()).isEqualTo("declined");
    assertThat(requests.get(0)).startsWith(orgId + " ");
  }

  @Test
  void rejectsMalformedTraceIds() throws Exception {
    mockMvc.perform(get("/api/v1/organizations/" + orgId + "/traces/not-a-trace").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_TRACE_ID"));
    assertThat(requests).isEmpty();
  }
}
