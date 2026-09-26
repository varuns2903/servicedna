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
}
