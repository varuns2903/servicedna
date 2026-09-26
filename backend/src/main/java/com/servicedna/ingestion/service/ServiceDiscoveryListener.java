package com.servicedna.ingestion.service;

import com.servicedna.ingestion.event.TraceBatchReceivedEvent;
import com.servicedna.service.dto.TelemetryIdentity;
import com.servicedna.service.service.ServiceRegistryService;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Registers services from their telemetry: the first batch of spans from a service creates it,
 * and later batches keep its metadata (language, version, last seen) current.
 *
 * <p>Runs on the ingestion request thread, so an identity already handled in the last
 * {@link #REFRESH_INTERVAL} is skipped without touching the database.
 */
@Component
public class ServiceDiscoveryListener {

  private static final Logger log = LoggerFactory.getLogger(ServiceDiscoveryListener.class);
  static final Duration REFRESH_INTERVAL = Duration.ofMinutes(1);
  /** OpenTelemetry SDKs report this when OTEL_SERVICE_NAME isn't set; it identifies nothing. */
  private static final String UNKNOWN_SERVICE_PREFIX = "unknown_service";

  private final ServiceRegistryService serviceRegistryService;
  private final Map<String, Handled> recentlyHandled = new ConcurrentHashMap<>();

  /** {@code serviceId} is null when registration was refused by the plan limit. */
  private record Handled(UUID serviceId, Instant at) {}

  public ServiceDiscoveryListener(ServiceRegistryService serviceRegistryService) {
    this.serviceRegistryService = serviceRegistryService;
  }

  @EventListener
  public void onTraceBatch(TraceBatchReceivedEvent event) {
    for (ResourceSpans resourceSpans : event.request().getResourceSpansList()) {
      TelemetryIdentity identity = identityOf(resourceSpans.getResource().getAttributesList());
      if (identity == null) {
        continue;
      }
      // Registration is best-effort: failing here would fail the export and make the client
      // re-send spans that are already stored.
      try {
        register(event.organizationId(), identity);
      } catch (RuntimeException e) {
        log.warn("Registering service {} from telemetry failed: {}", identity.name(), e.getMessage());
      }
    }
  }

  /** @return the service id, or empty when the organization's plan has no room for it */
  public Optional<UUID> register(UUID organizationId, TelemetryIdentity identity) {
    String cacheKey = organizationId + "|" + identity;
    Instant now = Instant.now();
    Handled last = recentlyHandled.get(cacheKey);
    if (last != null && last.at().isAfter(now.minus(REFRESH_INTERVAL))) {
      return Optional.ofNullable(last.serviceId());
    }

    Optional<UUID> serviceId;
    try {
      serviceId = serviceRegistryService.registerFromTelemetry(organizationId, identity);
    } catch (DataIntegrityViolationException concurrentlyCreated) {
      // Another batch registered the same service between our lookup and insert.
      serviceId = serviceRegistryService.registerFromTelemetry(organizationId, identity);
    }
    if (serviceId.isEmpty()) {
      log.warn(
          "Not registering {} for organization {}: plan service limit reached",
          identity.name(),
          organizationId);
    }
    recentlyHandled.put(cacheKey, new Handled(serviceId.orElse(null), now));
    if (recentlyHandled.size() > 50_000) {
      recentlyHandled.entrySet().removeIf(e -> e.getValue().at().isBefore(now.minus(REFRESH_INTERVAL)));
    }
    return serviceId;
  }

  /** Null when the resource doesn't name its service. */
  static TelemetryIdentity identityOf(List<KeyValue> attributes) {
    String name = attribute(attributes, "service.name");
    if (name == null || name.isBlank() || name.startsWith(UNKNOWN_SERVICE_PREFIX)) {
      return null;
    }
    String environment = attribute(attributes, "deployment.environment.name");
    if (environment == null) {
      environment = attribute(attributes, "deployment.environment"); // pre-1.27 semantic conventions
    }
    return new TelemetryIdentity(
        truncate(name.trim(), 255),
        truncate(environment, 64),
        truncate(attribute(attributes, "telemetry.sdk.language"), 32),
        truncate(attribute(attributes, "service.version"), 64),
        truncate(attribute(attributes, "cloud.region"), 255),
        truncate(attribute(attributes, "servicedna.health.url"), 2048));
  }

  private static String attribute(List<KeyValue> attributes, String key) {
    for (KeyValue kv : attributes) {
      if (kv.getKey().equals(key) && kv.getValue().hasStringValue()) {
        String value = kv.getValue().getStringValue();
        return value.isBlank() ? null : value;
      }
    }
    return null;
  }

  private static String truncate(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }
}
