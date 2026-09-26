package com.servicedna.logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.ingestion.dto.CreateIngestionKeyRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogsTest {

  /** Stands in for Loki: its OTLP receiver and its query API. */
  private static final HttpServer loki;
  private static final List<String> requests = new CopyOnWriteArrayList<>();
  private static final AtomicInteger ingestStatus = new AtomicInteger(204);

  static {
    try {
      loki = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
    loki.createContext("/", exchange -> {
      exchange.getRequestBody().readAllBytes();
      String uri = URLDecoder.decode(exchange.getRequestURI().toString(), StandardCharsets.UTF_8);
      requests.add(exchange.getRequestHeaders().getFirst("X-Scope-OrgID") + " " + uri);
      if (uri.startsWith("/otlp/v1/logs")) {
        exchange.sendResponseHeaders(ingestStatus.get(), -1);
        exchange.close();
        return;
      }
      int code = 200;
      String body;
      if (uri.contains("broken")) {
        code = 400;
        body = "parse error at line 1, col 2: syntax error";
      } else {
        body = """
            {"status":"success","data":{"resultType":"streams","result":[
              {"stream":{"service_name":"order-service","deployment_environment_name":"dev","severity_text":"ERROR",
                "detected_level":"error","trace_id":"4bf92f3577b34da6a3ce929d0e0e4736","span_id":"00f067aa0ba902b7",
                "orderId":"o-17","process_pid":"12","service_instance_id":"x"},
               "values":[["1760000000000000000","payment failed for order o-17"]]},
              {"stream":{"service_name":"payment-service","detected_level":"info"},
               "values":[["1760000001000000000","charge declined"]]}]}}""";
      }
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(code, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    loki.start();
  }

  @DynamicPropertySource
  static void lokiUrl(DynamicPropertyRegistry registry) {
    registry.add("log-store.url", () -> "http://127.0.0.1:" + loki.getAddress().getPort());
  }

  @AfterAll
  static void stop() {
    loki.stop(0);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private String token;
  private String orgId;

  @BeforeEach
  void setUp() throws Exception {
    requests.clear();
    ingestStatus.set(204);
    token = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new RegisterRequest("logs-" + UUID.randomUUID() + "@example.com", "password123"))))
        .andReturn().getResponse().getContentAsString()).get("token").asText();
    orgId = objectMapper.readTree(mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Logs Org"))))
        .andReturn().getResponse().getContentAsString()).get("id").asText();
  }

  private String ingestionKey() throws Exception {
    return objectMapper.readTree(mockMvc.perform(post("/api/v1/organizations/" + orgId + "/ingestion-keys")
            .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new CreateIngestionKeyRequest("logs"))))
        .andReturn().getResponse().getContentAsString()).get("key").asText();
  }

  private static byte[] batch() {
    return ExportLogsServiceRequest.newBuilder()
        .addResourceLogs(ResourceLogs.newBuilder().addScopeLogs(ScopeLogs.newBuilder()
            .addLogRecords(LogRecord.newBuilder().setBody(AnyValue.newBuilder().setStringValue("hello")))))
        .build().toByteArray();
  }

  @Test
  void otlpLogsAreStoredAsTheKeysOrganization() throws Exception {
    mockMvc.perform(post("/api/v1/otlp/v1/logs").header("x-servicedna-key", ingestionKey())
            .contentType("application/x-protobuf").content(batch()))
        .andExpect(status().isOk());
    assertThat(requests).containsExactly(orgId + " /otlp/v1/logs");
  }

  @Test
  void rejectsBadKeysAndAsksForARetryWhenTheStoreIsDown() throws Exception {
    mockMvc.perform(post("/api/v1/otlp/v1/logs").header("x-servicedna-key", "sdna_ik_nope")
            .contentType("application/x-protobuf").content(batch()))
        .andExpect(status().isUnauthorized());
    ingestStatus.set(500);
    mockMvc.perform(post("/api/v1/otlp/v1/logs").header("x-servicedna-key", ingestionKey())
            .contentType("application/x-protobuf").content(batch()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("LOG_STORE_UNAVAILABLE"));
  }

  @Test
  void searchTurnsFiltersIntoLogQlAndFlattensStreams() throws Exception {
    JsonNode result = objectMapper.readTree(mockMvc.perform(get("/api/v1/organizations/" + orgId + "/logs")
            .header("Authorization", "Bearer " + token)
            .param("service", "order-service").param("environment", "dev").param("level", "error")
            .param("text", "o-17 (x)").param("traceId", "4BF92F3577B34DA6A3CE929D0E0E4736")
            .param("attribute", "orderId=o-17", "http.status_code>=500"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

    assertThat(result.get("query").asText()).isEqualTo(
        "{service_name=\"order-service\", deployment_environment_name=\"dev\"}"
            + " | trace_id=\"4bf92f3577b34da6a3ce929d0e0e4736\" | detected_level=~\"error|fatal|critical\""
            + " |~ \"(?i)o-17 \\\\(x\\\\)\" | orderId=\"o-17\" | http_status_code >= 500");
    assertThat(requests.get(0)).startsWith(orgId + " /loki/api/v1/query_range?query=" + result.get("query").asText() + "&start=");
    assertThat(requests.get(0)).contains("&direction=backward");

    JsonNode entries = result.get("entries");
    assertThat(entries).hasSize(2);
    assertThat(entries.get(0).get("service").asText()).isEqualTo("payment-service"); // newest first
    JsonNode e = entries.get(1);
    assertThat(e.get("body").asText()).isEqualTo("payment failed for order o-17");
    assertThat(e.get("level").asText()).isEqualTo("error");
    assertThat(e.get("environment").asText()).isEqualTo("dev");
    assertThat(e.get("traceId").asText()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    assertThat(e.get("attributes").size()).isEqualTo(1);
    assertThat(e.get("attributes").get("orderId").asText()).isEqualTo("o-17");
  }

  @Test
  void badFiltersAndBadLogQlAreRejected() throws Exception {
    mockMvc.perform(get("/api/v1/organizations/" + orgId + "/logs").header("Authorization", "Bearer " + token).param("level", "loud"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_FILTER"));
    mockMvc.perform(get("/api/v1/organizations/" + orgId + "/logs").header("Authorization", "Bearer " + token).param("traceId", "zz"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_TRACE_ID"));
    mockMvc.perform(get("/api/v1/organizations/" + orgId + "/logs").header("Authorization", "Bearer " + token).param("q", "{broken"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_QUERY"));
  }
}
