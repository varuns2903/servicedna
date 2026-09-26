package com.servicedna.follow;

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
class FollowTest {

  /** Plays both Tempo (/api/search) and Loki (/loki/...). */
  private static final HttpServer stores;
  private static final List<String> requests = new CopyOnWriteArrayList<>();

  static {
    try {
      stores = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
    stores.createContext("/", exchange -> {
      String uri = URLDecoder.decode(exchange.getRequestURI().toString(), StandardCharsets.UTF_8);
      requests.add(uri);
      String body = uri.startsWith("/api/search")
          ? """
            {"traces":[
              {"traceID":"aaaa0000000000000000000000000001","rootServiceName":"api-gateway","rootTraceName":"POST /api/orders",
               "startTimeUnixNano":"1760000000000000000","durationMs":40,"spanSets":[],
               "serviceStats":{"api-gateway":{"spanCount":3},"order-service":{"spanCount":4}}},
              {"traceID":"bbbb0000000000000000000000000002","rootServiceName":"refund-job","rootTraceName":"refund batch",
               "startTimeUnixNano":"1760003600000000000","durationMs":900,"spanSets":[],
               "serviceStats":{"refund-job":{"spanCount":2,"errorCount":1}}}]}"""
          : """
            {"status":"success","data":{"resultType":"streams","result":[
              {"stream":{"service_name":"notification-service","detected_level":"info"},
               "values":[["1760000001000000000","emailed receipt for o-17"]]}]}}""";
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    stores.start();
  }

  @DynamicPropertySource
  static void urls(DynamicPropertyRegistry registry) {
    registry.add("trace-store.query-url", () -> "http://127.0.0.1:" + stores.getAddress().getPort());
    registry.add("log-store.url", () -> "http://127.0.0.1:" + stores.getAddress().getPort());
  }

  @AfterAll
  static void stop() {
    stores.stop(0);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  private String token;
  private String orgId;

  @BeforeEach
  void setUp() throws Exception {
    requests.clear();
    token = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new RegisterRequest("follow-" + UUID.randomUUID() + "@example.com", "password123"))))
        .andReturn().getResponse().getContentAsString()).get("token").asText();
    orgId = objectMapper.readTree(mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Follow Org"))))
        .andReturn().getResponse().getContentAsString()).get("id").asText();
  }

  @Test
  void aKeyIsFollowedAcrossSeparateTracesAndLogs() throws Exception {
    JsonNode story = objectMapper.readTree(mockMvc.perform(get("/api/v1/organizations/" + orgId + "/follow")
            .header("Authorization", "Bearer " + token).param("key", "orderId").param("value", "o-17"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

    assertThat(story.get("traceQuery").asText()).isEqualTo(
        "{ span.sdna.key.orderId = \"o-17\" || .orderId = \"o-17\" || span.sdna.capture.orderId = \"o-17\""
            + " || span.messaging.kafka.message.key = \"o-17\""
            + " || span.sdna.request.body =~ \"(.*[^A-Za-z0-9_-])?o-17([^A-Za-z0-9_-].*)?\""
            + " || span.sdna.response.body =~ \"(.*[^A-Za-z0-9_-])?o-17([^A-Za-z0-9_-].*)?\" }");
    assertThat(story.get("logQuery").asText()).isEqualTo("{service_name=~\".+\"} |~ \"(^|[^A-Za-z0-9_-])o-17([^A-Za-z0-9_-]|$)\"");
    assertThat(story.get("traces")).hasSize(2);
    assertThat(story.get("logs").get(0).get("body").asText()).isEqualTo("emailed receipt for o-17");
    List<String> services = objectMapper.convertValue(story.get("services"), List.class);
    assertThat(services).containsExactly("api-gateway", "notification-service", "order-service", "refund-job");
    assertThat(story.get("firstSeen").asText()).isEqualTo("2025-10-09T08:53:20Z");
    assertThat(story.get("lastSeen").asText()).isEqualTo("2025-10-09T09:53:20Z");
  }

  @Test
  void keysMustBeNamedAndValued() throws Exception {
    mockMvc.perform(get("/api/v1/organizations/" + orgId + "/follow").header("Authorization", "Bearer " + token)
            .param("key", "order id\" || true").param("value", "o-17"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_FILTER"));
    mockMvc.perform(get("/api/v1/organizations/" + orgId + "/follow").header("Authorization", "Bearer " + token)
            .param("key", "orderId").param("value", " "))
        .andExpect(status().isBadRequest());
    assertThat(requests).isEmpty();
  }
}
