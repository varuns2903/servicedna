package com.servicedna.telemetry.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.ingestion.service.IngestionKeyService;
import com.servicedna.ingestion.service.ServiceDiscoveryListener;
import com.servicedna.service.dto.TelemetryIdentity;
import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.telemetry.dto.PingRequest;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

@org.springframework.stereotype.Service
public class PingService {

  private final ServiceRepository serviceRepository;
  private final ServiceStatusRecorder statusRecorder;
  private final StringRedisTemplate redisTemplate;
  private final IngestionKeyService ingestionKeyService;
  private final ServiceDiscoveryListener serviceDiscovery;

  public PingService(
      ServiceRepository serviceRepository,
      ServiceStatusRecorder statusRecorder,
      StringRedisTemplate redisTemplate,
      IngestionKeyService ingestionKeyService,
      ServiceDiscoveryListener serviceDiscovery) {
    this.serviceRepository = serviceRepository;
    this.statusRecorder = statusRecorder;
    this.redisTemplate = redisTemplate;
    this.ingestionKeyService = ingestionKeyService;
    this.serviceDiscovery = serviceDiscovery;
  }

  public void processPing(String apiKey, PingRequest request) {
    // Resolved by lookup rather than by prefix alone: a per-service key is random after "sdna_"
    // and could, rarely, begin like an ingestion key.
    Optional<UUID> organizationId = ingestionKeyService.authenticate(apiKey);
    if (organizationId.isPresent()) {
      processOrganizationPing(organizationId.get(), request);
    } else {
      processServicePing(apiKey, request);
    }
  }

  /**
   * A ping sent with an organization ingestion key names its service, which is registered on first
   * contact like services discovered from traces — so SDK heartbeats need no per-service key.
   */
  private void processOrganizationPing(UUID organizationId, PingRequest request) {
    if (request.service() == null || request.service().isBlank()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "SERVICE_REQUIRED",
          "Pings sent with an organization ingestion key must name their service.");
    }
    String environment =
        request.environment() == null || request.environment().isBlank() ? null : request.environment().trim();
    UUID serviceId =
        serviceDiscovery
            .register(
                organizationId,
                new TelemetryIdentity(request.service().trim(), environment, null, null, null, null))
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.FORBIDDEN,
                        "PLAN_LIMIT_REACHED",
                        "Your plan's service limit is reached; this service can't be registered."));
    statusRecorder.record(serviceId, request.status(), request.latencyMs(), request.message());
  }

  private void processServicePing(String apiKey, PingRequest request) {
    String cacheKey = "apikey:" + apiKey;
    String serviceIdStr = redisTemplate.opsForValue().get(cacheKey);

    Service service;
    if (serviceIdStr != null) {
      service =
          serviceRepository
              .findById(UUID.fromString(serviceIdStr))
              .orElseThrow(
                  () ->
                      new ApiException(
                          HttpStatus.UNAUTHORIZED, "INVALID_API_KEY", "Invalid API Key"));
    } else {
      service =
          serviceRepository
              .findByApiKey(apiKey)
              .orElseThrow(
                  () ->
                      new ApiException(
                          HttpStatus.UNAUTHORIZED, "INVALID_API_KEY", "Invalid API Key"));
      redisTemplate.opsForValue().set(cacheKey, service.getId().toString(), Duration.ofHours(1));
    }

    statusRecorder.record(
        service.getId(), request.status(), request.latencyMs(), request.message());
  }
}
