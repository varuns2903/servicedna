package com.servicedna.ingestion;

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
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.dto.CreateServiceRequest;
import com.servicedna.telemetry.dto.PingRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SelfRegistrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private String token;
  private String orgId;
  private String key;

  @BeforeEach
  void setUp() throws Exception {
    token =
        call(post("/api/v1/auth/register")
                .content(json(new RegisterRequest("selfreg-" + UUID.randomUUID() + "@example.com", "password123"))))
            .get("token")
            .asText();
    orgId = call(post("/api/v1/organizations").content(json(new CreateOrganizationRequest("Self Reg Org")))).get("id").asText();
    key =
        call(post("/api/v1/organizations/" + orgId + "/ingestion-keys").content(json(new CreateIngestionKeyRequest("test"))))
            .get("key")
            .asText();
  }

  @Test
  void firstTelemetryRegistersTheServiceWithItsMetadata() throws Exception {
    export(
        resource(
            "service.name", "checkout",
            "deployment.environment.name", "prod",
            "telemetry.sdk.language", "java",
            "service.version", "1.4.2",
            "servicedna.health.url", "http://checkout:8080/actuator/health"));

    List<JsonNode> services = services();
    assertThat(services).hasSize(1);
    JsonNode service = services.get(0);
    assertThat(service.get("name").asText()).isEqualTo("checkout");
    assertThat(service.get("environment").asText()).isEqualTo("prod");
    assertThat(service.get("language").asText()).isEqualTo("java");
    assertThat(service.get("version").asText()).isEqualTo("1.4.2");
    assertThat(service.get("source").asText()).isEqualTo("TELEMETRY");
    assertThat(service.get("healthCheckUrl").asText()).isEqualTo("http://checkout:8080/actuator/health");
    assertThat(service.get("lastTelemetryAt").isNull()).isFalse();
  }

  @Test
  void laterTelemetryUpdatesTheSameService() throws Exception {
    export(resource("service.name", "checkout", "deployment.environment", "prod", "service.version", "1.0.0"));
    export(resource("service.name", "checkout", "deployment.environment", "prod", "service.version", "1.1.0"));

    List<JsonNode> services = services();
    assertThat(services).hasSize(1);
    assertThat(services.get(0).get("version").asText()).isEqualTo("1.1.0");
  }

  @Test
  void environmentsAreSeparateServices() throws Exception {
    export(resource("service.name", "checkout", "deployment.environment.name", "prod"));
    export(resource("service.name", "checkout", "deployment.environment.name", "staging"));

    assertThat(services()).extracting(s -> s.get("environment").asText()).containsExactlyInAnyOrder("prod", "staging");
  }

  @Test
  void adoptsAManuallyRegisteredServiceOfTheSameName() throws Exception {
    call(post("/api/v1/organizations/" + orgId + "/services")
        .content(json(new CreateServiceRequest("checkout", "Takes payments", null, null, null))));

    export(resource("service.name", "checkout", "deployment.environment.name", "prod"));

    List<JsonNode> services = services();
    assertThat(services).hasSize(1);
    assertThat(services.get(0).get("environment").asText()).isEqualTo("prod");
    assertThat(services.get(0).get("source").asText()).isEqualTo("MANUAL");
    assertThat(services.get(0).get("description").asText()).isEqualTo("Takes payments");
  }

  @Test
  void ignoresServicesWithoutAName() throws Exception {
    export(resource("service.name", "unknown_service:java"));
    export(resource("telemetry.sdk.language", "go"));

    assertThat(services()).isEmpty();
  }

  @Test
  void planLimitStopsRegistrationButSpansAreStillAccepted() throws Exception {
    for (int i = 1; i <= 3; i++) {
      call(post("/api/v1/organizations/" + orgId + "/services")
          .content(json(new CreateServiceRequest("manual-" + i, null, null, null, null))));
    }

    export(resource("service.name", "one-too-many"));

    assertThat(services()).extracting(s -> s.get("name").asText()).doesNotContain("one-too-many");
  }

  @Test
  void pingsWithTheOrganizationKeyRegisterTheirService() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/ping")
                .header("X-API-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new PingRequest(ServiceStatus.HEALTHY, 12, "ok", "billing", "prod"))))
        .andExpect(status().isOk());

    JsonNode service = services().get(0);
    assertThat(service.get("name").asText()).isEqualTo("billing");
    assertThat(service.get("environment").asText()).isEqualTo("prod");
    assertThat(service.get("status").asText()).isEqualTo("HEALTHY");

    mockMvc
        .perform(
            post("/api/v1/ping")
                .header("X-API-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new PingRequest(ServiceStatus.HEALTHY, 12, "ok", null, null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("SERVICE_REQUIRED"));
  }

  private void export(Map<String, String> resourceAttributes) throws Exception {
    Resource.Builder resource = Resource.newBuilder();
    resourceAttributes.forEach(
        (k, v) -> resource.addAttributes(KeyValue.newBuilder().setKey(k).setValue(AnyValue.newBuilder().setStringValue(v))));
    byte[] body =
        ExportTraceServiceRequest.newBuilder()
            .addResourceSpans(
                ResourceSpans.newBuilder()
                    .setResource(resource)
                    .addScopeSpans(ScopeSpans.newBuilder().addSpans(Span.newBuilder().setName("GET /"))))
            .build()
            .toByteArray();
    mockMvc
        .perform(
            post("/api/v1/otlp/v1/traces")
                .header("x-servicedna-key", key)
                .contentType("application/x-protobuf")
                .content(body))
        .andExpect(status().isOk());
  }

  private static Map<String, String> resource(String... keyValues) {
    Map<String, String> attributes = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      attributes.put(keyValues[i], keyValues[i + 1]);
    }
    return attributes;
  }

  private List<JsonNode> services() throws Exception {
    List<JsonNode> result = new ArrayList<>();
    call(get("/api/v1/organizations/" + orgId + "/services")).forEach(result::add);
    return result;
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
