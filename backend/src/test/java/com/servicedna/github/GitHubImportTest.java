package com.servicedna.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GitHubImportTest {

  private static final HttpServer github;
  private static final List<String> authHeaders = new CopyOnWriteArrayList<>();

  private static final String ORDERS_YAML = """
      service: order-service
      owner: team-checkout
      tier: critical
      slo: 99.95
      health: http://order-service:4004/health
      dependencies: [payment-service]
      alerts:
        - condition: STATUS_DOWN
          open_incident: critical
      """;

  private static final Map<String, String> RESPONSES = Map.of(
      "/orgs/acme/repos?per_page=100&type=all&page=1", """
          [{"name":"orders","full_name":"acme/orders","html_url":"https://github.com/acme/orders","description":"Orders","archived":false},
           {"name":"tools","full_name":"acme/tools","html_url":"https://github.com/acme/tools","description":"Internal tools","archived":false},
           {"name":"bad","full_name":"acme/bad","html_url":"https://github.com/acme/bad","archived":false},
           {"name":"legacy","full_name":"acme/legacy","html_url":"https://github.com/acme/legacy","archived":true}]""",
      "/users/solo/repos?per_page=100&type=all&page=1", "[]",
      "/repos/acme/orders", "{\"name\":\"orders\",\"full_name\":\"acme/orders\",\"html_url\":\"https://github.com/acme/orders\",\"description\":\"Orders\"}",
      "/repos/acme/tools", "{\"name\":\"tools\",\"full_name\":\"acme/tools\",\"html_url\":\"https://github.com/acme/tools\",\"description\":\"Internal tools\"}",
      "/repos/acme/bad", "{\"name\":\"bad\",\"full_name\":\"acme/bad\",\"html_url\":\"https://github.com/acme/bad\"}",
      "/repos/acme/orders/contents/servicedna.yaml", content(ORDERS_YAML),
      "/repos/acme/bad/contents/servicedna.yaml", content("service: bad\nteir: high\n"));

  private static String content(String yaml) {
    return "{\"content\":\"" + Base64.getMimeEncoder().encodeToString(yaml.getBytes(StandardCharsets.UTF_8)).replace("\r\n", "\\n") + "\"}";
  }

  static {
    try {
      github = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
    github.createContext("/", exchange -> {
      authHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
      String body = RESPONSES.get(exchange.getRequestURI().toString());
      if (body == null) {
        exchange.sendResponseHeaders(404, -1);
      } else {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
      }
      exchange.close();
    });
    github.start();
  }

  @DynamicPropertySource
  static void githubUrl(DynamicPropertyRegistry registry) {
    registry.add("github.api-url", () -> "http://127.0.0.1:" + github.getAddress().getPort());
  }

  @AfterAll
  static void stop() {
    github.stop(0);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  private String token;
  private String orgId;

  @BeforeEach
  void setUp() throws Exception {
    authHeaders.clear();
    token = call(post("/api/v1/auth/register").content(objectMapper.writeValueAsString(new RegisterRequest("gh-" + UUID.randomUUID() + "@example.com", "password123")))).get("token").asText();
    orgId = call(post("/api/v1/organizations").content(objectMapper.writeValueAsString(new CreateOrganizationRequest("GitHub Org")))).get("id").asText();
  }

  @Test
  void previewShowsEachRepositoryAndWhatItsManifestSays() throws Exception {
    JsonNode repos = call(post("/api/v1/organizations/" + orgId + "/github/import/preview").content("{\"owner\":\"acme\",\"token\":\"ghp_x\"}"));
    assertThat(repos).hasSize(4);
    JsonNode orders = repos.get(0);
    assertThat(orders.get("hasManifest").asBoolean()).isTrue();
    assertThat(orders.get("service").asText()).isEqualTo("order-service");
    assertThat(orders.get("owner").asText()).isEqualTo("team-checkout");
    assertThat(repos.get(1).get("hasManifest").asBoolean()).isFalse();
    assertThat(repos.get(1).get("service").asText()).isEqualTo("tools");
    assertThat(repos.get(2).get("manifestError").asText()).contains("teir");
    assertThat(repos.get(3).get("archived").asBoolean()).isTrue();
    assertThat(authHeaders).allMatch(h -> h.equals("Bearer ghp_x"));

    // A personal account isn't an organization: its repositories are listed instead.
    assertThat(call(post("/api/v1/organizations/" + orgId + "/github/import/preview").content("{\"owner\":\"solo\"}"))).isEmpty();
  }

  @Test
  void importRegistersEachChosenRepositoryOnItsOwn() throws Exception {
    JsonNode results = call(post("/api/v1/organizations/" + orgId + "/github/import")
        .content("{\"owner\":\"acme\",\"token\":\"ghp_x\",\"repositories\":[\"acme/orders\",\"acme/tools\",\"acme/bad\"]}"));
    assertThat(results.get(0).get("ok").asBoolean()).isTrue();
    assertThat(results.get(0).get("message").asText()).isEqualTo("registered from servicedna.yaml, 1 alert rule; not registered yet: payment-service");
    assertThat(results.get(1).get("ok").asBoolean()).isTrue();
    assertThat(results.get(2).get("ok").asBoolean()).isFalse();
    assertThat(results.get(2).get("message").asText()).contains("teir");

    JsonNode services = call(get("/api/v1/organizations/" + orgId + "/services"));
    assertThat(services).hasSize(2);
    JsonNode orders = null;
    JsonNode tools = null;
    for (JsonNode s : services) {
      if (s.get("name").asText().equals("order-service")) orders = s;
      if (s.get("name").asText().equals("tools")) tools = s;
    }
    assertThat(orders.get("owner").asText()).isEqualTo("team-checkout");
    assertThat(orders.get("repositoryUrl").asText()).isEqualTo("https://github.com/acme/orders");
    assertThat(orders.get("sloTargetPercentage").asDouble()).isEqualTo(99.95);
    assertThat(tools.get("description").asText()).isEqualTo("Internal tools");

    JsonNode again = call(post("/api/v1/organizations/" + orgId + "/github/import/preview").content("{\"owner\":\"acme\"}"));
    assertThat(again.get(0).get("registered").asBoolean()).isTrue();
  }

  private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
    request.contentType(MediaType.APPLICATION_JSON);
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return objectMapper.readTree(mockMvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
  }
}
