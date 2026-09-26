package io.github.varuns2903.servicedna;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks the service's own health endpoint on an interval and reports the result to ServiceDNA,
 * which registers the service on first contact. Uses the JDK HTTP client, which OpenTelemetry's
 * Spring starter doesn't instrument, so heartbeats aren't traced as the service's traffic.
 */
final class ServiceDnaHeartbeat {

  private static final Logger log = LoggerFactory.getLogger(ServiceDnaHeartbeat.class);

  private final String serviceDnaUrl;
  private final String key;
  private final String serviceName;
  private final String environment;
  private final Duration degradedThreshold;
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper json = new ObjectMapper();
  private ScheduledExecutorService scheduler;

  ServiceDnaHeartbeat(
      String serviceDnaUrl, String key, String serviceName, String environment, Duration degradedThreshold) {
    this.serviceDnaUrl = serviceDnaUrl;
    this.key = key;
    this.serviceName = serviceName;
    this.environment = environment;
    this.degradedThreshold = degradedThreshold;
  }

  synchronized void start(URI localHealthUrl, Duration interval) {
    if (scheduler != null || interval.isZero() || interval.isNegative()) {
      return;
    }
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "servicedna-heartbeat");
              t.setDaemon(true);
              return t;
            });
    scheduler.scheduleAtFixedRate(
        () -> beat(localHealthUrl), interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
  }

  synchronized void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
      scheduler = null;
    }
  }

  void beat(URI localHealthUrl) {
    long start = System.nanoTime();
    String status = "DOWN";
    String message = "health check failed";
    try {
      HttpResponse<String> res =
          client.send(
              HttpRequest.newBuilder(localHealthUrl).timeout(Duration.ofSeconds(5)).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      message = describe(res.body(), res.statusCode());
      if (res.statusCode() < 300) {
        status = elapsed(start).compareTo(degradedThreshold) > 0 ? "DEGRADED" : "HEALTHY";
      }
    } catch (Exception e) {
      message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("status", status);
    payload.put("latencyMs", elapsed(start).toMillis());
    payload.put("message", message.length() > 500 ? message.substring(0, 500) : message);
    payload.put("service", serviceName);
    payload.put("environment", environment);
    try {
      HttpResponse<Void> res =
          client.send(
              HttpRequest.newBuilder(URI.create(serviceDnaUrl + "/api/v1/ping"))
                  .timeout(Duration.ofSeconds(5))
                  .header("Content-Type", "application/json")
                  .header("X-API-Key", key)
                  .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                  .build(),
              HttpResponse.BodyHandlers.discarding());
      if (res.statusCode() >= 300) {
        log.warn("[servicedna] heartbeat rejected: HTTP {}", res.statusCode());
      }
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.warn("[servicedna] heartbeat failed: {}", e.getMessage());
    }
  }

  private String describe(String body, int statusCode) {
    try {
      JsonNode node = json.readTree(body);
      if (node.hasNonNull("message")) {
        return node.get("message").asText();
      }
      if (node.hasNonNull("status")) {
        return node.get("status").asText(); // actuator: {"status":"UP"}
      }
    } catch (Exception ignored) {
      // not JSON
    }
    return "HTTP " + statusCode;
  }

  private static Duration elapsed(long startNanos) {
    return Duration.ofNanos(System.nanoTime() - startNanos);
  }
}
