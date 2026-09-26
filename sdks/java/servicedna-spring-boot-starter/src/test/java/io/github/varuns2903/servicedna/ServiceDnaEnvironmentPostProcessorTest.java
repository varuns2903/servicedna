package io.github.varuns2903.servicedna;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ServiceDnaEnvironmentPostProcessorTest {

  @Test
  void disablesOpenTelemetryWithoutUrlAndKey() {
    assertThat(ServiceDnaEnvironmentPostProcessor.otelProperties(new MockEnvironment()))
        .containsExactly(Map.entry("otel.sdk.disabled", "true"));
  }

  @Test
  void pointsTheExporterAtServiceDna() {
    MockEnvironment env =
        new MockEnvironment()
            .withProperty("servicedna.url", "http://sdna:8080/")
            .withProperty("servicedna.key", "sdna_ik_x")
            .withProperty("servicedna.env", "prod")
            .withProperty("servicedna.health-url", "http://orders:8080/actuator/health");

    Map<String, Object> props = ServiceDnaEnvironmentPostProcessor.otelProperties(env);

    assertThat(props)
        .containsEntry("otel.exporter.otlp.traces.endpoint", "http://sdna:8080/api/v1/otlp/v1/traces")
        .containsEntry("otel.exporter.otlp.traces.protocol", "http/protobuf")
        .containsEntry("otel.exporter.otlp.traces.headers", "x-servicedna-key=sdna_ik_x")
        .containsEntry(
            "otel.resource.attributes",
            "deployment.environment.name=prod,servicedna.health.url=http://orders:8080/actuator/health")
        .doesNotContainKey("otel.service.name") // OpenTelemetry defaults to spring.application.name
        .doesNotContainKey("otel.sdk.disabled");
  }

  @Test
  void healthPathIsActuatorsWhenActuatorIsPresent() {
    assertThat(ServiceDnaAutoConfiguration.healthPath(new MockEnvironment())).isEqualTo("/actuator/health");
    assertThat(
            ServiceDnaAutoConfiguration.healthPath(
                new MockEnvironment().withProperty("servicedna.health-path", "/healthz")))
        .isEqualTo("/healthz");
  }

  @Test
  void acceptsTheOpenTelemetryVersionItWasBuiltWith() {
    ServiceDnaEnvironmentPostProcessor.requireCompatibleOpenTelemetry(); // no exception
  }
}
