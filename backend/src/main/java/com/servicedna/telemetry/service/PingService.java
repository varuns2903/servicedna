package com.servicedna.telemetry.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.telemetry.dto.PingRequest;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class PingService {

  private final ServiceRepository serviceRepository;
  private final ServiceStatusRecorder statusRecorder;
  private final StringRedisTemplate redisTemplate;

  public PingService(
      ServiceRepository serviceRepository,
      ServiceStatusRecorder statusRecorder,
      StringRedisTemplate redisTemplate) {
    this.serviceRepository = serviceRepository;
    this.statusRecorder = statusRecorder;
    this.redisTemplate = redisTemplate;
  }

  @Transactional
  public void processPing(String apiKey, PingRequest request) {
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
