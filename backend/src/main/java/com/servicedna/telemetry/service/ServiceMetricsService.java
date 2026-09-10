package com.servicedna.telemetry.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.Service;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.telemetry.domain.ServicePing;
import com.servicedna.telemetry.dto.MetricPointDto;
import com.servicedna.telemetry.dto.ServiceMetricsDto;
import com.servicedna.telemetry.repository.ServicePingRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ServiceMetricsService {

  private final ServiceRepository serviceRepository;
  private final ServicePingRepository servicePingRepository;
  private final OrganizationService organizationService;

  public ServiceMetricsService(
      ServiceRepository serviceRepository,
      ServicePingRepository servicePingRepository,
      OrganizationService organizationService) {
    this.serviceRepository = serviceRepository;
    this.servicePingRepository = servicePingRepository;
    this.organizationService = organizationService;
  }

  public ServiceMetricsDto getMetrics(
      UUID organizationId, UUID serviceId, UUID userId, String range) {
    organizationService.validateUserAccess(organizationId, userId);

    Service service =
        serviceRepository
            .findByOrganizationIdAndId(organizationId, serviceId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));

    Duration window = parseRange(range);
    OffsetDateTime since = OffsetDateTime.now().minus(window);

    List<ServicePing> pings =
        servicePingRepository.findByServiceIdAndCreatedAtAfterOrderByCreatedAtAsc(
            service.getId(), since);

    if (pings.isEmpty()) {
      return new ServiceMetricsDto(100.0, null, 0, List.of());
    }

    long healthyCount = pings.stream().filter(p -> p.getStatus() == ServiceStatus.HEALTHY).count();
    double uptimePercentage = (healthyCount * 100.0) / pings.size();

    double avgLatencyMs =
        pings.stream()
            .filter(p -> p.getLatencyMs() != null)
            .mapToInt(ServicePing::getLatencyMs)
            .average()
            .orElse(0);

    List<MetricPointDto> dataPoints =
        pings.stream()
            .map(p -> new MetricPointDto(p.getCreatedAt(), p.getStatus(), p.getLatencyMs()))
            .toList();

    return new ServiceMetricsDto(
        uptimePercentage,
        pings.stream().anyMatch(p -> p.getLatencyMs() != null) ? avgLatencyMs : null,
        pings.size(),
        dataPoints);
  }

  private Duration parseRange(String range) {
    return switch (range) {
      case "24h" -> Duration.ofHours(24);
      case "7d" -> Duration.ofDays(7);
      case "30d" -> Duration.ofDays(30);
      case null -> Duration.ofHours(24);
      default ->
          throw new ApiException(
              HttpStatus.BAD_REQUEST, "INVALID_RANGE", "range must be one of: 24h, 7d, 30d");
    };
  }
}
