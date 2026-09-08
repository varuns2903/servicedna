package com.servicedna.service.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.MaintenanceStatus;
import com.servicedna.service.domain.MaintenanceWindow;
import com.servicedna.service.domain.Service;
import com.servicedna.service.dto.CreateMaintenanceWindowRequest;
import com.servicedna.service.dto.MaintenanceWindowDto;
import com.servicedna.service.repository.MaintenanceWindowRepository;
import com.servicedna.service.repository.ServiceRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class MaintenanceWindowService {

  private final MaintenanceWindowRepository maintenanceWindowRepository;
  private final ServiceRepository serviceRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationService organizationService;

  public MaintenanceWindowService(
      MaintenanceWindowRepository maintenanceWindowRepository,
      ServiceRepository serviceRepository,
      OrganizationRepository organizationRepository,
      OrganizationService organizationService) {
    this.maintenanceWindowRepository = maintenanceWindowRepository;
    this.serviceRepository = serviceRepository;
    this.organizationRepository = organizationRepository;
    this.organizationService = organizationService;
  }

  @Transactional
  public MaintenanceWindowDto createMaintenanceWindow(
      UUID organizationId, CreateMaintenanceWindowRequest request, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    Organization organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "ORG_NOT_FOUND", "Organization not found"));

    Service service =
        serviceRepository
            .findByOrganizationIdAndId(organizationId, request.serviceId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));

    if (request.startTime().isAfter(request.endTime())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_TIME", "Start time must be before end time");
    }

    MaintenanceWindow window =
        new MaintenanceWindow(
            UUID.randomUUID(),
            organization,
            service,
            request.title(),
            request.description(),
            request.startTime(),
            request.endTime(),
            MaintenanceStatus.SCHEDULED);

    window = maintenanceWindowRepository.save(window);
    return mapToDto(window);
  }

  @Transactional(readOnly = true)
  public List<MaintenanceWindowDto> getMaintenanceWindows(UUID organizationId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    return maintenanceWindowRepository
        .findByOrganizationIdOrderByStartTimeDesc(organizationId)
        .stream()
        .map(this::mapToDto)
        .collect(Collectors.toList());
  }

  @Transactional
  public MaintenanceWindowDto updateMaintenanceStatus(
      UUID organizationId, UUID windowId, MaintenanceStatus status, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    MaintenanceWindow window =
        maintenanceWindowRepository
            .findByOrganizationIdAndId(organizationId, windowId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "WINDOW_NOT_FOUND", "Maintenance window not found"));

    window.setStatus(status);
    window = maintenanceWindowRepository.save(window);
    return mapToDto(window);
  }

  private MaintenanceWindowDto mapToDto(MaintenanceWindow window) {
    return new MaintenanceWindowDto(
        window.getId(),
        window.getOrganization().getId(),
        window.getService().getId(),
        window.getTitle(),
        window.getDescription(),
        window.getStartTime(),
        window.getEndTime(),
        window.getStatus(),
        window.getCreatedAt(),
        window.getUpdatedAt());
  }
}
