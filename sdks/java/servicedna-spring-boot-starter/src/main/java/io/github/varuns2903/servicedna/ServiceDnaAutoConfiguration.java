package io.github.varuns2903.servicedna;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

/**
 * ServiceDNA on top of OpenTelemetry's Spring Boot starter: drops health-check spans and sends
 * heartbeats. Tracing itself is configured by {@link ServiceDnaEnvironmentPostProcessor}.
 */
@AutoConfiguration
@ConditionalOnProperty({"servicedna.url", "servicedna.key"})
public class ServiceDnaAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(ServiceDnaAutoConfiguration.class);

  /** Actuator's health endpoint when actuator is on the classpath, otherwise {@code /health}. */
  static String healthPath(Environment env) {
    String configured = env.getProperty("servicedna.health-path");
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    if (ClassUtils.isPresent("org.springframework.boot.actuate.health.HealthEndpoint", null)) {
      String base = env.getProperty("management.endpoints.web.base-path", "/actuator");
      return base.replaceAll("/+$", "") + "/health";
    }
    return "/health";
  }

  @Bean
  AutoConfigurationCustomizerProvider serviceDnaHealthCheckSampler(Environment env) {
    String healthPath = healthPath(env);
    return customizer ->
        customizer.addSamplerCustomizer(
            (sampler, config) -> new HealthCheckDroppingSampler(sampler, healthPath));
  }

  /** Body capture for test runs (and optionally failed requests), in servlet (Spring MVC) applications. */
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
      type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
  @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(
      name = "org.springframework.web.util.ContentCachingRequestWrapper")
  static class CaptureConfiguration {
    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<ServiceDnaCaptureFilter> serviceDnaCaptureFilter(Environment env) {
      // SERVICEDNA_CAPTURE_ON_ERROR=true (or servicedna.capture-on-error): bodies of failed requests too.
      boolean onError = env.getProperty("servicedna.capture-on-error", Boolean.class, false);
      var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(new ServiceDnaCaptureFilter(onError));
      // Inside OpenTelemetry's server filter, so its span and extracted baggage are current.
      registration.setOrder(org.springframework.core.Ordered.LOWEST_PRECEDENCE - 100);
      return registration;
    }
  }

  @Bean(destroyMethod = "stop")
  ServiceDnaHeartbeat serviceDnaHeartbeat(Environment env) {
    return new ServiceDnaHeartbeat(
        serviceDnaUrl(env),
        env.getProperty("servicedna.key"),
        serviceName(env),
        env.getProperty("servicedna.env"),
        Duration.ofMillis(env.getProperty("servicedna.degraded-ms", Long.class, 2000L)));
  }

  /** Starts the heartbeat once the web server's port is known. */
  @Bean
  ApplicationListener<WebServerInitializedEvent> serviceDnaHeartbeatStarter(
      Environment env, ServiceDnaHeartbeat heartbeat) {
    Duration interval = Duration.ofMillis(env.getProperty("servicedna.heartbeat-ms", Long.class, 15000L));
    return event -> {
      if (event.getApplicationContext().getParent() != null) {
        return; // management server on a separate port; the main server starts the heartbeat
      }
      String local =
          env.getProperty(
              "servicedna.local-health-url",
              "http://127.0.0.1:" + event.getWebServer().getPort() + healthPath(env));
      heartbeat.start(URI.create(local), interval);
      String environment = env.getProperty("servicedna.env");
      log.info(
          "[servicedna] sending {}{} telemetry to {}",
          serviceName(env),
          environment != null ? " (" + environment + ")" : "",
          serviceDnaUrl(env));
    };
  }

  private static String serviceDnaUrl(Environment env) {
    return env.getProperty("servicedna.url", "").replaceAll("/+$", "");
  }

  private static String serviceName(Environment env) {
    return env.getProperty(
        "servicedna.service", env.getProperty("spring.application.name", "unknown_service:java"));
  }
}
