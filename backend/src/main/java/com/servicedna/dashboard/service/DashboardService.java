package com.servicedna.dashboard.service;

import com.servicedna.dashboard.dto.DashboardServiceOverviewDto;
import com.servicedna.dashboard.dto.DashboardSummaryDto;
import com.servicedna.incident.domain.Incident;
import com.servicedna.incident.domain.IncidentStatus;
import com.servicedna.incident.dto.IncidentDto;
import com.servicedna.incident.repository.IncidentRepository;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.Service;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.telemetry.domain.ServicePing;
import com.servicedna.telemetry.repository.ServicePingRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class DashboardService {

  private final ServiceRepository serviceRepository;
  private final IncidentRepository incidentRepository;
  private final ServicePingRepository servicePingRepository;
  private final OrganizationService organizationService;

  public DashboardService(
      ServiceRepository serviceRepository,
      IncidentRepository incidentRepository,
      ServicePingRepository servicePingRepository,
      OrganizationService organizationService) {
    this.serviceRepository = serviceRepository;
    this.incidentRepository = incidentRepository;
    this.servicePingRepository = servicePingRepository;
    this.organizationService = organizationService;
  }

  @org.springframework.cache.annotation.Cacheable(
      value = "dashboardSummary",
      key = "#organizationId")
  @Transactional(readOnly = true)
  public DashboardSummaryDto getDashboardSummary(UUID organizationId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    return getDashboardSummaryInternal(organizationId);
  }

  @org.springframework.cache.annotation.CacheEvict(
      value = "dashboardSummary",
      key = "#organizationId")
  public void invalidateDashboardCache(UUID organizationId) {}

  @Transactional(readOnly = true)
  public DashboardSummaryDto getDashboardSummaryInternal(UUID organizationId) {

    List<Service> services = serviceRepository.findByOrganizationId(organizationId);
    int totalServices = services.size();
    int healthyServices =
        (int) services.stream().filter(s -> s.getStatus() == ServiceStatus.HEALTHY).count();

    List<Incident> allIncidents =
        incidentRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    List<Incident> activeIncidents =
        allIncidents.stream().filter(i -> i.getStatus() != IncidentStatus.RESOLVED).toList();

    List<IncidentDto> activeIncidentDtos =
        activeIncidents.stream().map(this::mapIncidentToDto).collect(Collectors.toList());

    List<DashboardServiceOverviewDto> servicesOverview =
        services.stream().map(this::mapServiceToOverview).collect(Collectors.toList());

    return new DashboardSummaryDto(
        totalServices,
        healthyServices,
        activeIncidents.size(),
        activeIncidentDtos,
        servicesOverview);
  }

  private DashboardServiceOverviewDto mapServiceToOverview(Service service) {
    // Just grab the last few pings to calculate an average latency
    // In a real prod app with millions of pings, this should be an aggregate SQL query or Redis
    // cached
    List<ServicePing> recentPings =
        servicePingRepository.findByServiceIdOrderByCreatedAtDesc(service.getId()).stream()
            .limit(10)
            .toList();

    Double avgLatency = null;
    if (!recentPings.isEmpty()) {
      double sum = 0;
      int count = 0;
      for (ServicePing p : recentPings) {
        if (p.getLatencyMs() != null) {
          sum += p.getLatencyMs();
          count++;
        }
      }
      if (count > 0) {
        avgLatency = sum / count;
      }
    }

    return new DashboardServiceOverviewDto(
        service.getId(), service.getName(), service.getStatus(), avgLatency);
  }

  private IncidentDto mapIncidentToDto(Incident incident) {
    List<UUID> affectedServiceIds =
        incident.getAffectedServices().stream().map(Service::getId).collect(Collectors.toList());

    return new IncidentDto(
        incident.getId(),
        incident.getOrganization().getId(),
        incident.getCreatedBy().getId(),
        incident.getTitle(),
        incident.getDescription(),
        incident.getStatus(),
        incident.getSeverity(),
        affectedServiceIds,
        incident.getResolvedAt(),
        incident.getAcknowledgedAt(),
        incident.getEscalatedAt(),
        incident.getCreatedAt(),
        incident.getUpdatedAt());
  }
}
