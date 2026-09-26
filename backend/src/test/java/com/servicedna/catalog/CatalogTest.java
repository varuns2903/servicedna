package com.servicedna.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.catalog.dto.CatalogDto;
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
class CatalogTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CallAggregator aggregator;

  private String token;
  private String orgId;

  @BeforeEach
  void setUp() throws Exception {
    token =
        call(post("/api/v1/auth/register").content(json(new RegisterRequest("catalog-" + UUID.randomUUID() + "@example.com", "password123"))))
            .get("token")
            .asText();
    orgId = call(post("/api/v1/organizations").content(json(new CreateOrganizationRequest("Catalog Org")))).get("id").asText();
  }

  @Test
  void scanRegistersTheServiceItsOperationsAndItsDependencies() throws Exception {
    String payments = call(post("/api/v1/organizations/" + orgId + "/services").content(json(new CreateServiceRequest("payment-service", null, null, null, null)))).get("id").asText();

    JsonNode result =
        call(post("/api/v1/organizations/" + orgId + "/catalog/scan")
            .content(json(new CatalogDto.ScanRequest(
                "order-service",
                null,
                List.of(
                    new CatalogDto.OperationSpec(Protocol.HTTP, "POST /orders", "OPENAPI", "Place an order", "{\"type\":\"object\"}"),
                    new CatalogDto.OperationSpec(Protocol.HTTP, "GET /orders/{id}", "OPENAPI", null, null)),
                List.of("payment-service", "fraud-service", "order-service")))));

    assertThat(result.get("operations").asInt()).isEqualTo(2);
    assertThat(texts(result.get("dependenciesAdded"))).containsExactly("payment-service");
    assertThat(texts(result.get("dependenciesUnknown"))).containsExactly("fraud-service");

    String orders = result.get("serviceId").asText();
    JsonNode service = call(get("/api/v1/organizations/" + orgId + "/services/" + orders));
    assertThat(texts(service.get("dependencyIds"))).containsExactly(payments);

    JsonNode operations = call(get("/api/v1/organizations/" + orgId + "/services/" + orders + "/operations"));
    assertThat(operations).hasSize(2);
    assertThat(operations.get(0).get("name").asText()).isEqualTo("GET /orders/{id}");
    assertThat(operations.get(1).get("requestSchema").asText()).isEqualTo("{\"type\":\"object\"}");
  }

  @Test
  void rescanningReplacesTheOperations() throws Exception {
    scan("orders", new CatalogDto.OperationSpec(Protocol.HTTP, "POST /orders", "OPENAPI", null, null));
    JsonNode result = scan("orders", new CatalogDto.OperationSpec(Protocol.GRPC, "Orders/Create", "PROTO", null, null));

    JsonNode operations = call(get("/api/v1/organizations/" + orgId + "/services/" + result.get("serviceId").asText() + "/operations"));
    assertThat(operations).hasSize(1);
    assertThat(operations.get(0).get("name").asText()).isEqualTo("Orders/Create");
    assertThat(operations.get(0).get("protocol").asText()).isEqualTo("GRPC");
  }

  @Test
  void operationsIncludeWhatTrafficUses() throws Exception {
    String orders = scan("orders", new CatalogDto.OperationSpec(Protocol.HTTP, "POST /orders", "OPENAPI", null, null)).get("serviceId").asText();
    String gateway = call(post("/api/v1/organizations/" + orgId + "/services").content(json(new CreateServiceRequest("gateway", null, null, null, null)))).get("id").asText();
    for (String operation : List.of("POST /orders", "POST /orders", "GET /orders/{id}")) {
      aggregator.record(UUID.fromString(orgId), Instant.now(),
          new CallKey(UUID.fromString(gateway), "", UUID.fromString(orders), TargetKind.SERVICE, orders, operation, Protocol.HTTP), 5, false);
    }
    aggregator.flush();

    JsonNode operations = call(get("/api/v1/organizations/" + orgId + "/services/" + orders + "/operations"));
    assertThat(operations).hasSize(2);
    JsonNode spec = operations.get(0);
    assertThat(spec.get("name").asText()).isEqualTo("POST /orders");
    assertThat(spec.get("source").asText()).isEqualTo("OPENAPI");
    assertThat(spec.get("observed").asBoolean()).isTrue();
    assertThat(spec.get("callsLast24h").asLong()).isEqualTo(2);
    assertThat(operations.get(1).get("source").asText()).isEqualTo("TRAFFIC");
  }

  @Test
  void entryOperationsNothingInstrumentedCallsAreListedToo() throws Exception {
    String gateway = call(post("/api/v1/organizations/" + orgId + "/services").content(json(new CreateServiceRequest("gateway", null, null, null, null)))).get("id").asText();
    String orders = call(post("/api/v1/organizations/" + orgId + "/services").content(json(new CreateServiceRequest("orders", null, null, null, null)))).get("id").asText();
    aggregator.record(UUID.fromString(orgId), Instant.now(),
        new CallKey(UUID.fromString(gateway), "POST /api/orders", UUID.fromString(orders), TargetKind.SERVICE, orders, "POST /orders", Protocol.HTTP), 5, false);
    aggregator.record(UUID.fromString(orgId), Instant.now(),
        new CallKey(UUID.fromString(gateway), "/shop.Orders/Place", UUID.fromString(orders), TargetKind.SERVICE, orders, "POST /orders", Protocol.HTTP), 5, false);
    aggregator.flush();

    JsonNode operations = call(get("/api/v1/organizations/" + orgId + "/services/" + gateway + "/operations"));
    List<String> listed = new ArrayList<>();
    operations.forEach(o -> listed.add(o.get("protocol").asText() + " " + o.get("name").asText() + " " + o.get("source").asText()));
    assertThat(listed).containsExactlyInAnyOrder("HTTP POST /api/orders TRAFFIC", "GRPC /shop.Orders/Place TRAFFIC");
  }

  @Test
  void protocolsAreInferredFromOperationNames() {
    assertThat(com.servicedna.catalog.service.CatalogService.protocolOf("GET /products/{id}")).isEqualTo(Protocol.HTTP);
    assertThat(com.servicedna.catalog.service.CatalogService.protocolOf("process order.created")).isEqualTo(Protocol.MESSAGING);
    assertThat(com.servicedna.catalog.service.CatalogService.protocolOf("order.created publish")).isEqualTo(Protocol.MESSAGING);
    assertThat(com.servicedna.catalog.service.CatalogService.protocolOf("query Products")).isEqualTo(Protocol.GRAPHQL);
    assertThat(com.servicedna.catalog.service.CatalogService.protocolOf("cleanup")).isEqualTo(Protocol.OTHER);
  }

  @Test
  void aScanWithoutOperationsKeepsTheCatalog() throws Exception {
    String orders = scan("orders", new CatalogDto.OperationSpec(Protocol.HTTP, "POST /orders", "OPENAPI", null, null)).get("serviceId").asText();
    call(post("/api/v1/organizations/" + orgId + "/catalog/scan").content(json(new CatalogDto.ScanRequest("orders", null, null, List.of()))));

    assertThat(call(get("/api/v1/organizations/" + orgId + "/services/" + orders + "/operations"))).hasSize(1);
  }

  @Test
  void aScanWithoutEnvironmentAppliesToTheExistingService() throws Exception {
    JsonNode dev = call(post("/api/v1/organizations/" + orgId + "/catalog/scan")
        .content(json(new CatalogDto.ScanRequest("orders", "dev", List.of(), List.of()))));
    JsonNode unspecified = scan("orders", new CatalogDto.OperationSpec(Protocol.HTTP, "POST /orders", "OPENAPI", null, null));

    assertThat(unspecified.get("serviceId").asText()).isEqualTo(dev.get("serviceId").asText());
    assertThat(call(get("/api/v1/organizations/" + orgId + "/services"))).hasSize(1);
  }

  private JsonNode scan(String service, CatalogDto.OperationSpec... operations) throws Exception {
    return call(post("/api/v1/organizations/" + orgId + "/catalog/scan")
        .content(json(new CatalogDto.ScanRequest(service, null, List.of(operations), List.of()))));
  }

  private static List<String> texts(JsonNode array) {
    List<String> out = new ArrayList<>();
    array.forEach(n -> out.add(n.asText()));
    return out;
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

  @Test
  void servicednaYamlSetsMetadataAndManagesItsAlertRules() throws Exception {
    String orders = scan("orders", new CatalogDto.OperationSpec(Protocol.HTTP, "POST /orders", "OPENAPI", null, null)).get("serviceId").asText();
    String rules = "/api/v1/organizations/" + orgId + "/services/" + orders + "/alert-rules";
    call(post(rules).content("{\"condition\":\"STATUS_DEGRADED\",\"incidentSeverity\":\"MINOR\"}")); // made by hand
    // Read first, so any cached view would be stale after the scan.
    call(get("/api/v1/organizations/" + orgId + "/services/" + orders));
    call(get("/api/v1/organizations/" + orgId + "/services"));

    String yaml = """
        {"service":"orders","dependencies":[],
         "metadata":{"description":"Takes orders","owner":"team-checkout","tier":"critical","slo":99.95,
                     "healthUrl":"http://orders:4004/health","repositoryUrl":"https://github.com/acme/orders"},
         "alerts":[{"condition":"STATUS_DOWN","incidentSeverity":"CRITICAL"},
                   {"condition":"LATENCY_ABOVE","threshold":800,"windowMinutes":5,"incidentSeverity":"MAJOR"}]}""";
    JsonNode result = call(post("/api/v1/organizations/" + orgId + "/catalog/scan").content(yaml));
    assertThat(texts(result.get("updated"))).containsExactly("description", "owner", "tier", "healthUrl", "repositoryUrl", "slo");
    assertThat(result.get("alertRules").asInt()).isEqualTo(2);

    JsonNode service = call(get("/api/v1/organizations/" + orgId + "/services/" + orders));
    assertThat(service.get("owner").asText()).isEqualTo("team-checkout");
    assertThat(service.get("tier").asText()).isEqualTo("critical");
    assertThat(service.get("sloTargetPercentage").asDouble()).isEqualTo(99.95);
    assertThat(service.get("healthCheckUrl").asText()).isEqualTo("http://orders:4004/health");
    assertThat(call(get("/api/v1/organizations/" + orgId + "/services")).get(0).get("owner").asText()).isEqualTo("team-checkout");
    assertThat(call(get(rules))).hasSize(3);

    // Rescanning replaces the rules the file manages, and only those; no alerts key leaves them be.
    call(post("/api/v1/organizations/" + orgId + "/catalog/scan").content(
        "{\"service\":\"orders\",\"dependencies\":[],\"alerts\":[{\"condition\":\"STATUS_DOWN\",\"incidentSeverity\":\"MAJOR\"}]}"));
    call(post("/api/v1/organizations/" + orgId + "/catalog/scan").content("{\"service\":\"orders\",\"dependencies\":[]}"));
    // A rule is deleted through its own service's path only.
    String ruleId = call(get(rules)).get(0).get("id").asText();
    String other = call(post("/api/v1/organizations/" + orgId + "/services").content(json(new CreateServiceRequest("other", null, null, null, null)))).get("id").asText();
    assertThat(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .delete("/api/v1/organizations/" + orgId + "/services/" + other + "/alert-rules/" + ruleId)
            .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus()).isEqualTo(404);
    List<String> now = new ArrayList<>();
    call(get(rules)).forEach(r -> now.add(r.get("condition").asText() + " " + r.get("incidentSeverity").asText() + " " + r.path("managedBy").asText("-")));
    assertThat(now).containsExactlyInAnyOrder("STATUS_DEGRADED MINOR -", "STATUS_DOWN MAJOR CATALOG");
  }

  @Test
  void invalidMetadataAndRulesAreRejectedBeforeAnythingChanges() throws Exception {
    mockMvc.perform(post("/api/v1/organizations/" + orgId + "/catalog/scan").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token)
            .content("{\"service\":\"orders\",\"dependencies\":[],\"metadata\":{\"tier\":\"urgent\",\"healthUrl\":\"/health\"}}"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    mockMvc.perform(post("/api/v1/organizations/" + orgId + "/catalog/scan").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token)
            .content("{\"service\":\"orders\",\"dependencies\":[],\"alerts\":[{\"condition\":\"STATUS_DOWN\"}]}"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
  }
}
