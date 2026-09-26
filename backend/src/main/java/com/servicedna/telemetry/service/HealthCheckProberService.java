package com.servicedna.telemetry.service;

import com.servicedna.service.domain.Service;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.repository.ServiceRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
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
  private final ServiceStatusRecorder statusRecorder;
  private final RestTemplate restTemplate;
  private final long degradedThresholdMs;

  public HealthCheckProberService(
      ServiceRepository serviceRepository,
      ServiceStatusRecorder statusRecorder,
      RestTemplateBuilder restTemplateBuilder,
      @Value("${health-check.timeout-ms:5000}") long timeoutMs,
      @Value("${health-check.degraded-threshold-ms:2000}") long degradedThresholdMs) {
    this.serviceRepository = serviceRepository;
    this.statusRecorder = statusRecorder;
    this.restTemplate =
        restTemplateBuilder
            .setConnectTimeout(Duration.ofMillis(timeoutMs))
            .setReadTimeout(Duration.ofMillis(timeoutMs))
            .build();
    this.degradedThresholdMs = degradedThresholdMs;
  }

  @Scheduled(fixedRateString = "${health-check.interval-ms:30000}")
  public void probeAll() {
    for (Service service : serviceRepository.findByHealthCheckUrlIsNotNull()) {
      try {
        probeOne(service);
      } catch (Exception e) {
        log.warn("Health check probe failed unexpectedly for service {}: {}", service.getId(), e.getMessage());
      }
    }
  }

  boolean probeOne(Service service) {
    ProbeResult result = check(service.getHealthCheckUrl());
    return statusRecorder.record(service.getId(), result.status(), (int) result.latencyMs(), null);
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
