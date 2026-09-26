package io.github.varuns2903.servicedna;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Translates ServiceDNA settings ({@code servicedna.*} properties or {@code SERVICEDNA_*}
 * environment variables) into the {@code otel.*} properties OpenTelemetry's Spring Boot starter
 * reads. Added with the lowest precedence, so anything the application sets explicitly wins.
 * Without a URL and key, the OpenTelemetry SDK is disabled.
 */
public class ServiceDnaEnvironmentPostProcessor implements EnvironmentPostProcessor {

  static final String SOURCE_NAME = "servicedna";
  /** The OpenTelemetry API version the bundled instrumentation is built against. */
  static final String REQUIRED_OPENTELEMETRY_VERSION = "1.65.0";

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    requireCompatibleOpenTelemetry();
    environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, otelProperties(environment)));
  }

  /**
   * Spring Boot manages an older OpenTelemetry API version than OpenTelemetry's Spring starter
   * needs, and a Boot-parent project's managed version wins. Without this check the application
   * fails later with a NoClassDefFoundError that doesn't say what to change.
   */
  static void requireCompatibleOpenTelemetry() {
    if (isPresent("io.opentelemetry.common.ComponentLoader")) {
      return;
    }
    String found =
        isPresent("io.opentelemetry.api.OpenTelemetry")
            ? String.valueOf(io.opentelemetry.api.OpenTelemetry.class.getPackage().getImplementationVersion())
            : "none";
    throw new IllegalStateException(
        "ServiceDNA needs OpenTelemetry API "
            + REQUIRED_OPENTELEMETRY_VERSION
            + "+ but found "
            + found
            + " (Spring Boot manages an older version). Add <opentelemetry.version>"
            + REQUIRED_OPENTELEMETRY_VERSION
            + "</opentelemetry.version> to your pom's <properties>, or for Gradle set"
            + " ext['opentelemetry.version'] = '"
            + REQUIRED_OPENTELEMETRY_VERSION
            + "'.");
  }

  private static boolean isPresent(String className) {
    try {
      Class.forName(className, false, ServiceDnaEnvironmentPostProcessor.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  static Map<String, Object> otelProperties(ConfigurableEnvironment env) {
    Map<String, Object> props = new LinkedHashMap<>();
    String url = stripTrailingSlash(env.getProperty("servicedna.url", ""));
    String key = env.getProperty("servicedna.key", "");
    if (url.isEmpty() || key.isEmpty()) {
      props.put("otel.sdk.disabled", "true");
      return props;
    }

    props.put("otel.exporter.otlp.traces.endpoint", url + "/api/v1/otlp/v1/traces");
    props.put("otel.exporter.otlp.traces.protocol", "http/protobuf");
    props.put("otel.exporter.otlp.traces.headers", "x-servicedna-key=" + key);
    props.put("otel.metrics.exporter", "none");
    props.put("otel.logs.exporter", "none");

    String service = env.getProperty("servicedna.service");
    if (service != null && !service.isBlank()) {
      props.put("otel.service.name", service);
    }

    List<String> attributes = new ArrayList<>();
    addAttribute(attributes, "deployment.environment.name", env.getProperty("servicedna.env"));
    addAttribute(attributes, "service.version", env.getProperty("servicedna.version"));
    addAttribute(attributes, "servicedna.health.url", env.getProperty("servicedna.health-url"));
    if (!attributes.isEmpty()) {
      props.put("otel.resource.attributes", String.join(",", attributes));
    }
    return props;
  }

  private static void addAttribute(List<String> attributes, String key, String value) {
    if (value != null && !value.isBlank()) {
      attributes.add(key + "=" + value.trim());
    }
  }

  private static String stripTrailingSlash(String url) {
    return url.replaceAll("/+$", "");
  }
}
