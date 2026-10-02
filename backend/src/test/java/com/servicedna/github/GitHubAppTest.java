package com.servicedna.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.testrun.TestRun;
import com.servicedna.testrun.TestRunRepository;
import com.servicedna.testrun.TestRunStatus;
import com.servicedna.testrun.TestSuiteService;
import com.servicedna.traces.TraceDto;
import com.servicedna.traces.TraceQueryService;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {"test-runs.evaluate-ms=3600000", "github.app.poll=true", "github.app.poll-interval-ms=3600000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GitHubAppTest {

  private static final String SECRET = "whsec_test";
  private static final KeyPair KEYS;
  private static final HttpServer github;
  /** Fake GitHub's responses by "METHOD path"; requests it received, with their bodies. */
  private static final Map<String, String> routes = new ConcurrentHashMap<>();
  private static final List<String> received = new CopyOnWriteArrayList<>();
  private static final List<String> badJwts = new CopyOnWriteArrayList<>();

  private static final String MANIFEST = "service: order-service\nowner: team-checkout\ntier: critical\nalerts:\n  - condition: STATUS_DOWN\n    open_incident: CRITICAL\n";
  private static final String FLOW = """
      name: Checkout
      cases:
        - name: an order is placed
          request: {service: order-service, method: POST, path: /orders, body: {userId: u-1}}
          expect:
            - {entry: true, status: 201}
      """;

  static {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KEYS = generator.generateKeyPair();
      github = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    github.createContext("/", exchange -> {
      String path = URLDecoder.decode(exchange.getRequestURI().toString(), StandardCharsets.UTF_8);
      String request = exchange.getRequestMethod() + " " + path;
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      received.add(request + (body.isBlank() ? "" : " " + body));
      String auth = String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"));
      if (path.startsWith("/app/")) {
        try {
          Jwts.parser().verifyWith(KEYS.getPublic()).build().parseSignedClaims(auth.substring("Bearer ".length()));
        } catch (Exception e) {
          badJwts.add(path + ": " + e.getMessage());
        }
      }
      String response = routes.get(request);
      if (response == null && request.startsWith("PATCH ")) {
        response = "{}";
      }
      byte[] bytes = response == null ? new byte[0] : response.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(response == null ? 404 : 200, response == null ? -1 : bytes.length);
      if (response != null) {
        exchange.getResponseBody().write(bytes);
      }
      exchange.close();
    });
    github.start();
  }

  @DynamicPropertySource
  static void app(DynamicPropertyRegistry registry) {
    String url = "http://127.0.0.1:" + github.getAddress().getPort();
    registry.add("github.api-url", () -> url);
    registry.add("github.web-url", () -> url);
    registry.add("github.app.id", () -> "123");
    registry.add("github.app.slug", () -> "servicedna-test");
    registry.add("github.app.private-key", () -> pem("PRIVATE KEY", KEYS.getPrivate().getEncoded()));
    registry.add("github.app.webhook-secret", () -> SECRET);
    registry.add("github.app.client-id", () -> "Iv1.client");
    registry.add("github.app.client-secret", () -> "client-secret");
  }

  @AfterAll
  static void stop() {
    github.stop(0);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TestRunRepository runs;
  @Autowired private TestSuiteService suites;
  @Autowired private GitHubAppService app;
  @MockBean private TraceQueryService traces;

  private String token;
  private String orgId;
  /** Each test installs its own: an installation can belong to one organization only. */
  private long installation;

  @BeforeEach
  void setUp() throws Exception {
    routes.clear();
    installation = java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000, 1_000_000_000);
    received.clear();
    badJwts.clear();
    token = call(post("/api/v1/auth/register").content(objectMapper.writeValueAsString(new RegisterRequest("gh-app-" + UUID.randomUUID() + "@example.com", "password123")))).get("token").asText();
    orgId = call(post("/api/v1/organizations").content(objectMapper.writeValueAsString(new CreateOrganizationRequest("App Org")))).get("id").asText();
    call(post("/api/v1/organizations/" + orgId + "/services").content("{\"name\":\"order-service\",\"healthCheckUrl\":\"http://orders:4004/health\"}"));

    routes.put("POST /app/installations/" + installation + "/access_tokens", "{\"token\":\"ghs_installation\",\"expires_at\":\"" + Instant.now().plusSeconds(3600) + "\"}");
    routes.put("POST /login/oauth/access_token", "{\"access_token\":\"ghu_user\"}");
    routes.put("GET /user/installations?per_page=100", "{\"installations\":[{\"id\":" + installation + "}]}");
    routes.put("GET /app/installations/" + installation, "{\"id\":" + installation + ",\"account\":{\"login\":\"acme\"}}");
    routes.put("GET /installation/repositories?per_page=100&page=1", """
        {"repositories":[
          {"name":"orders","full_name":"acme/orders","html_url":"https://github.com/acme/orders","default_branch":"main"},
          {"name":"tools","full_name":"acme/tools","html_url":"https://github.com/acme/tools","default_branch":"main"}]}""");
    routes.put("GET /repos/acme/orders/contents/servicedna.yaml?ref=main", content(MANIFEST));
    when(traces.trace(any(UUID.class), anyString())).thenReturn(new TraceDto.Trace("t", Instant.now(), 5, List.of(
        new TraceDto.Span("1", null, "order-service", "POST /orders", "SERVER", Instant.now(), 5, false, null, Map.of(), List.of()))));
  }

  private String connect() throws Exception {
    JsonNode status = call(get("/api/v1/organizations/" + orgId + "/github/app"));
    assertThat(status.get("configured").asBoolean()).isTrue();
    String installUrl = status.get("installUrl").asText();
    assertThat(installUrl).contains("/apps/servicedna-test/installations/new?state=");
    String state = URLDecoder.decode(installUrl.substring(installUrl.indexOf("state=") + 6), StandardCharsets.UTF_8);
    JsonNode linked = call(post("/api/v1/organizations/" + orgId + "/github/installations")
        .content("{\"installationId\":" + installation + ",\"code\":\"oauth-code\",\"state\":\"" + state + "\"}"));
    assertThat(linked.has("account")).as("connect returned %s; GitHub saw %s", linked, received).isTrue();
    return linked.get("account").asText();
  }

  @Test
  void connectingLinksTheInstallationAndSyncsReposWithAManifest() throws Exception {
    assertThat(connect()).isEqualTo("acme");
    JsonNode service = awaitService("order-service", s -> "team-checkout".equals(s.path("owner").asText(null)));
    assertThat(service.get("tier").asText()).isEqualTo("critical");
    assertThat(call(get("/api/v1/organizations/" + orgId + "/services"))).hasSize(1); // tools has no servicedna.yaml
    assertThat(badJwts).isEmpty(); // the app's JWTs verify with its public key
    assertThat(received).anyMatch(r -> r.startsWith("POST /login/oauth/access_token") && r.contains("code=oauth-code"));

    JsonNode status = call(get("/api/v1/organizations/" + orgId + "/github/app"));
    assertThat(status.get("installations").get(0).get("installationId").asLong()).isEqualTo(installation);
  }

  @Test
  void aTamperedStateOrSomeoneElsesInstallationIsRejected() throws Exception {
    int tampered = mockMvc.perform(auth(post("/api/v1/organizations/" + orgId + "/github/installations"))
        .content("{\"installationId\":" + installation + ",\"code\":\"c\",\"state\":\"bm9wZQ.00\"}")).andReturn().getResponse().getStatus();
    assertThat(tampered).isEqualTo(400);

    routes.put("GET /user/installations?per_page=100", "{\"installations\":[{\"id\":7}]}");
    JsonNode status = call(get("/api/v1/organizations/" + orgId + "/github/app"));
    String url = status.get("installUrl").asText();
    String state = URLDecoder.decode(url.substring(url.indexOf("state=") + 6), StandardCharsets.UTF_8);
    int notYours = mockMvc.perform(auth(post("/api/v1/organizations/" + orgId + "/github/installations"))
        .content("{\"installationId\":" + installation + ",\"code\":\"c\",\"state\":\"" + state + "\"}")).andReturn().getResponse().getStatus();
    assertThat(notYours).isEqualTo(403);
  }

  @Test
  void webhooksMustBeSignedAndAPushOfTheManifestResyncs() throws Exception {
    connect();
    awaitService("order-service", s -> "team-checkout".equals(s.path("owner").asText(null)));
    assertThat(webhook("push", "{}", "sha256=00")).isEqualTo(401);

    routes.put("GET /repos/acme/orders/contents/servicedna.yaml?ref=main", content(MANIFEST.replace("team-checkout", "team-orders")));
    String push = """
        {"installation":{"id":%d},"ref":"refs/heads/main",
         "repository":{"name":"orders","full_name":"acme/orders","html_url":"https://github.com/acme/orders","default_branch":"main"},
         "commits":[{"added":[],"modified":["servicedna.yaml"]}]}""".formatted(installation);
    assertThat(webhook("push", push, null)).isEqualTo(202);
    awaitService("order-service", s -> "team-orders".equals(s.path("owner").asText(null)));
  }

  @Test
  void aPullRequestWithABadManifestFailsItsCheck() throws Exception {
    connect();
    routes.put("POST /repos/acme/orders/check-runs", "{\"id\":7}");
    routes.put("GET /repos/acme/orders/contents/servicedna.yaml?ref=abc123", content("service: order-service\nteir: high\n"));

    assertThat(webhook("pull_request", pullRequest(), null)).isEqualTo(202);
    String completed = awaitRequest("PATCH /repos/acme/orders/check-runs/7");
    assertThat(completed).contains("\"conclusion\":\"failure\"").contains("teir");
  }

  @Test
  void aPullRequestRunsItsFlowsAndReportsTheResults() throws Exception {
    connect();
    routes.put("POST /repos/acme/orders/check-runs", "{\"id\":8}");
    routes.put("GET /repos/acme/orders/contents/servicedna.yaml?ref=abc123", content(MANIFEST));
    routes.put("GET /repos/acme/orders/contents/flows?ref=abc123", "[{\"name\":\"checkout.yaml\",\"type\":\"file\"},{\"name\":\"README.md\",\"type\":\"file\"}]");
    routes.put("GET /repos/acme/orders/contents/flows/checkout.yaml?ref=abc123", content(FLOW));

    assertThat(webhook("pull_request", pullRequest(), null)).isEqualTo(202);
    TestRun run = awaitRun();
    // The runner sent the request and got a 201; the trace has arrived.
    run.setStatus(TestRunStatus.COMPLETED);
    run.setResult("{\"sent\":true,\"status\":201}");
    runs.save(run);
    suites.evaluate();

    String completed = awaitRequest("PATCH /repos/acme/orders/check-runs/8");
    assertThat(completed).contains("\"conclusion\":\"success\"").contains("1 passed, 0 failed")
        .contains("servicedna.yaml").contains("an order is placed").contains("/traces?trace=");
  }

  @Test
  void pollingNoticesPullRequestsManifestChangesAndUninstallsWithoutWebhooks() throws Exception {
    connect();
    awaitService("order-service", s -> "team-checkout".equals(s.path("owner").asText(null)));
    routes.put("POST /repos/acme/orders/check-runs", "{\"id\":9}");
    routes.put("GET /repos/acme/orders/contents/servicedna.yaml?ref=abc123", content(MANIFEST));
    routes.put("GET /repos/acme/tools/pulls?state=open&per_page=100", "[]");
    routes.put("GET /repos/acme/orders/pulls?state=open&per_page=100", """
        [{"number":5,"draft":false,"head":{"sha":"abc123"}},
         {"number":6,"draft":true,"head":{"sha":"draft1"}}]""");

    app.pollAll();
    assertThat(awaitRequest("PATCH /repos/acme/orders/check-runs/9")).contains("\"conclusion\":\"success\"");
    app.pollAll(); // nothing new: each head is checked once, drafts not at all
    assertThat(received.stream().filter(r -> r.startsWith("POST /repos/acme/orders/check-runs"))).hasSize(1)
        .allMatch(r -> r.contains("abc123"));

    routes.put("GET /repos/acme/orders/contents/servicedna.yaml?ref=main", content(MANIFEST.replace("team-checkout", "team-orders")));
    routes.put("GET /installation/repositories?per_page=100&page=1", """
        {"repositories":[{"name":"orders","full_name":"acme/orders","html_url":"https://github.com/acme/orders",
          "default_branch":"main","pushed_at":"2026-10-02T10:00:00Z"}]}""");
    app.pollAll();
    awaitService("order-service", s -> "team-orders".equals(s.path("owner").asText(null)));

    routes.remove("GET /app/installations/" + installation);
    app.pollAll();
    assertThat(call(get("/api/v1/organizations/" + orgId + "/github/app")).get("installations")).isEmpty();
  }

  @Test
  void theAppsPrivateKeyIsReadInEitherPemFormat() {
    byte[] pkcs8 = KEYS.getPrivate().getEncoded();
    byte[] pkcs1 = Arrays.copyOfRange(pkcs8, 26, pkcs8.length); // the RSAPrivateKey inside PKCS#8's wrapper
    assertThat(GitHubAppClient.parsePrivateKey(pem("RSA PRIVATE KEY", pkcs1))).isEqualTo(KEYS.getPrivate());
    assertThat(GitHubAppClient.parsePrivateKey(pem("PRIVATE KEY", pkcs8).replace("\n", "\\n"))).isEqualTo(KEYS.getPrivate());
  }

  // --- helpers ------------------------------------------------------------------------------

  private String pullRequest() {
    return """
        {"action":"opened","installation":{"id":%d},
         "repository":{"name":"orders","full_name":"acme/orders","html_url":"https://github.com/acme/orders","default_branch":"main"},
         "pull_request":{"draft":false,"head":{"sha":"abc123"}}}""".formatted(installation);
  }

  private int webhook(String event, String payload, String signature) throws Exception {
    return mockMvc.perform(post("/api/v1/github/webhook").contentType(MediaType.APPLICATION_JSON)
            .header("X-GitHub-Event", event)
            .header("X-Hub-Signature-256", signature != null ? signature : sign(payload))
            .content(payload))
        .andReturn().getResponse().getStatus();
  }

  private static String sign(String payload) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
  }

  private JsonNode awaitService(String name, java.util.function.Predicate<JsonNode> ready) throws Exception {
    for (int i = 0; i < 100; i++) {
      for (JsonNode s : call(get("/api/v1/organizations/" + orgId + "/services"))) {
        if (s.path("name").asText().equals(name) && ready.test(s)) {
          return s;
        }
      }
      Thread.sleep(100);
    }
    throw new AssertionError("service " + name + " never synced; GitHub saw " + received);
  }

  private String awaitRequest(String prefix) throws Exception {
    for (int i = 0; i < 100; i++) {
      for (String r : received) {
        if (r.startsWith(prefix)) {
          return r;
        }
      }
      Thread.sleep(100);
    }
    throw new AssertionError(prefix + " never sent; GitHub saw " + received);
  }

  private TestRun awaitRun() throws Exception {
    for (int i = 0; i < 100; i++) {
      var found = runs.findAll().stream().filter(r -> r.getOrganizationId().toString().equals(orgId)).findFirst();
      if (found.isPresent()) {
        return found.get();
      }
      Thread.sleep(100);
    }
    throw new AssertionError("no test run started; GitHub saw " + received);
  }

  private static String content(String text) {
    return "{\"content\":\"" + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "\"}";
  }

  private static String pem(String type, byte[] der) {
    return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der) + "\n-----END " + type + "-----\n";
  }

  private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
    return request.contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token);
  }

  private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
    return objectMapper.readTree(mockMvc.perform(auth(request)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
  }
}
