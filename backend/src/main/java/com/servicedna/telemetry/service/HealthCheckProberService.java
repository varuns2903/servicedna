package com.servicedna.telemetry.service;

import com.servicedna.alert.event.ServiceStatusChangedEvent;
import com.servicedna.alert.service.AlertEventPublisher;
import com.servicedna.dashboard.event.DashboardInvalidationEvent;
import com.servicedna.service.domain.Service;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.telemetry.domain.ServicePing;
import com.servicedna.telemetry.repository.ServicePingRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Actively polls each service's configured health check URL on an interval, instead of relying
 * only on the service pushing its own status via the ping API.
 */
@Component
public class HealthCheckProberService {

  private static final Logger log = LoggerFactory.getLogger(HealthCheckProberService.class);

  private final ServiceRepository serviceRepository;
  private final ServicePingRepository servicePingRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final AlertEventPublisher alertEventPublisher;
  private final CacheManager cacheManager;
  private final RestTemplate restTemplate;
  private final long degradedThresholdMs;

  public HealthCheckProberService(
      ServiceRepository serviceRepository,
      ServicePingRepository servicePingRepository,
      ApplicationEventPublisher eventPublisher,
      @Lazy AlertEventPublisher alertEventPublisher,
      CacheManager cacheManager,
      RestTemplateBuilder restTemplateBuilder,
      @Value("${health-check.timeout-ms:5000}") long timeoutMs,
      @Value("${health-check.degraded-threshold-ms:2000}") long degradedThresholdMs) {
    this.serviceRepository = serviceRepository;
    this.servicePingRepository = servicePingRepository;
    this.eventPublisher = eventPublisher;
    this.alertEventPublisher = alertEventPublisher;
    this.cacheManager = cacheManager;
    this.restTemplate =
        restTemplateBuilder
            .setConnectTimeout(Duration.ofMillis(timeoutMs))
            .setReadTimeout(Duration.ofMillis(timeoutMs))
            .build();
    this.degradedThresholdMs = degradedThresholdMs;
  }

  @Scheduled(fixedRateString = "${health-check.interval-ms:30000}")
  public void probeAll() {
    List<Service> services = serviceRepository.findByHealthCheckUrlIsNotNull();
    boolean anyChanged = false;
    for (Service service : services) {
      try {
        anyChanged |= probeOne(service);
      } catch (Exception e) {
        log.warn("Health check probe failed unexpectedly for service {}: {}", service.getId(), e.getMessage());
      }
    }

    // Evicted once after the whole batch, not per-service: evicting mid-loop lets a read that
    // lands between two updates re-cache a stale partial snapshot for the full 10-minute TTL,
    // since a service whose status doesn't change again never triggers a follow-up eviction.
    if (anyChanged) {
      evictServiceCaches();
    }
  }

  boolean probeOne(Service service) {
    ProbeResult result = check(service.getHealthCheckUrl());
    ServiceStatus newStatus = result.status();
    ServiceStatus oldStatus = service.getStatus();

    servicePingRepository.save(
        new ServicePing(UUID.randomUUID(), service, newStatus, (int) result.latencyMs(), null));

    if (oldStatus == newStatus) {
      return false;
    }

    service.setStatus(newStatus);
    serviceRepository.save(service);

    // The status write above already committed — a failure notifying downstream systems
    // (Kafka/alerting, the dashboard websocket push) must not be mistaken for the probe itself
    // having failed, and must not suppress the batch-level cache eviction below. Otherwise a
    // transient Kafka blip leaves the API serving a stale cached status until the 10-minute
    // cache TTL expires on its own.
    try {
      if (alertEventPublisher != null) {
        alertEventPublisher.publishStatusChangedEvent(
            new ServiceStatusChangedEvent(
                service.getId(), service.getOrganization().getId(), oldStatus, newStatus, OffsetDateTime.now()));
      }
      eventPublisher.publishEvent(new DashboardInvalidationEvent(this, service.getOrganization().getId()));
    } catch (Exception e) {
      log.warn(
          "Status for service {} updated to {} but notifying downstream listeners failed: {}",
          service.getId(),
          newStatus,
          e.getMessage());
    }

    return true;
  }

  /**
   * {@code services}/{@code publicStatus} are cached for 10 minutes ({@link
   * com.servicedna.common.config.CacheConfig}). Without an explicit evict here, a status flip
   * from this prober wouldn't be visible through the API until the cache entry expired on its
   * own — defeating the point of polling every 30 seconds.
   */
  private void evictServiceCaches() {
    for (String cacheName : new String[] {"services", "publicStatus"}) {
      Cache cache = cacheManager.getCache(cacheName);
      if (cache != null) {
        cache.clear();
      }
    }
  }

  private ProbeResult check(String healthCheckUrl) {
    long start = System.currentTimeMillis();
    try {
      HttpStatusCode status = restTemplate.getForEntity(healthCheckUrl, Void.class).getStatusCode();
      long latencyMs = System.currentTimeMillis() - start;

      if (!status.is2xxSuccessful()) {
        return new ProbeResult(ServiceStatus.DOWN, latencyMs);
      }
      return new ProbeResult(
          latencyMs > degradedThresholdMs ? ServiceStatus.DEGRADED : ServiceStatus.HEALTHY,
          latencyMs);
    } catch (RestClientException e) {
      return new ProbeResult(ServiceStatus.DOWN, System.currentTimeMillis() - start);
    }
  }

  private record ProbeResult(ServiceStatus status, long latencyMs) {}
}
