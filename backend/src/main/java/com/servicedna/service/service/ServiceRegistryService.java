package com.servicedna.service.service;

import com.servicedna.alert.event.ServiceStatusChangedEvent;
import com.servicedna.alert.service.AlertEventPublisher;
import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.event.AuditLogEvent;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.Service;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.dto.AddDependencyRequest;
import com.servicedna.service.dto.CreateServiceRequest;
import com.servicedna.service.dto.ServiceDto;
import com.servicedna.service.dto.UpdateServiceStatusRequest;
import com.servicedna.service.repository.ServiceRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.servicedna.dashboard.event.DashboardInvalidationEvent;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class ServiceRegistryService {

    private final ServiceRepository serviceRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationService organizationService;
    private final AlertEventPublisher alertEventPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    public ServiceRegistryService(
            ServiceRepository serviceRepository,
            OrganizationRepository organizationRepository,
            OrganizationService organizationService,
            @Lazy AlertEventPublisher alertEventPublisher,
            ApplicationEventPublisher eventPublisher
    ) {
        this.serviceRepository = serviceRepository;
        this.organizationRepository = organizationRepository;
        this.organizationService = organizationService;
        this.alertEventPublisher = alertEventPublisher;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ServiceDto createService(UUID organizationId, CreateServiceRequest request, UUID userId) {
        organizationService.validateUserAccess(organizationId, userId);

        if (serviceRepository.existsByOrganizationIdAndName(organizationId, request.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "SERVICE_EXISTS", "Service with this name already exists in the organization");
        }

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORG_NOT_FOUND", "Organization not found"));

        Service service = new Service(
                UUID.randomUUID(),
                organization,
                request.name(),
                request.description(),
                request.repositoryUrl(),
                request.region(),
                generateApiKey()
        );

        service = serviceRepository.save(service);
        eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
        eventPublisher.publishEvent(new AuditLogEvent(
                organizationId, userId, "CREATE_SERVICE", "Service", service.getId().toString(),
                "Created service " + service.getName(), null
        ));
        return mapToDto(service);
    }

    @Transactional(readOnly = true)
    public List<ServiceDto> getServices(UUID organizationId, UUID userId) {
        organizationService.validateUserAccess(organizationId, userId);
        
        return serviceRepository.findByOrganizationId(organizationId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ServiceDto getService(UUID organizationId, UUID serviceId, UUID userId) {
        organizationService.validateUserAccess(organizationId, userId);

        Service service = serviceRepository.findByOrganizationIdAndId(organizationId, serviceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));

        eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
        return mapToDto(service);
    }

    @Transactional
    public ServiceDto updateServiceStatus(UUID organizationId, UUID serviceId, UpdateServiceStatusRequest request, UUID userId) {
        organizationService.validateUserAccess(organizationId, userId);

        Service service = serviceRepository.findByOrganizationIdAndId(organizationId, serviceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));

        ServiceStatus oldStatus = service.getStatus();
        ServiceStatus newStatus = request.status();
        
        service.setStatus(newStatus);
        service = serviceRepository.save(service);

        if (oldStatus != newStatus && alertEventPublisher != null) {
            alertEventPublisher.publishStatusChangedEvent(new ServiceStatusChangedEvent(
                    service.getId(),
                    service.getOrganization().getId(),
                    oldStatus,
                    newStatus,
                    OffsetDateTime.now()
            ));
        }

        eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
        eventPublisher.publishEvent(new AuditLogEvent(
                organizationId, userId, "UPDATE_SERVICE_STATUS", "Service", service.getId().toString(),
                "Updated status from " + oldStatus + " to " + newStatus, null
        ));
        return mapToDto(service);
    }

    @Transactional
    public ServiceDto addDependency(UUID organizationId, UUID serviceId, AddDependencyRequest request, UUID userId) {
        organizationService.validateUserAccess(organizationId, userId);

        Service service = serviceRepository.findByOrganizationIdAndId(organizationId, serviceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));

        Service dependency = serviceRepository.findByOrganizationIdAndId(organizationId, request.dependsOnServiceId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEPENDENCY_NOT_FOUND", "Dependency service not found"));

        if (service.getId().equals(dependency.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEPENDENCY", "A service cannot depend on itself");
        }

        service.getDependencies().add(dependency);
        service = serviceRepository.save(service);

        eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
        return mapToDto(service);
    }

    @Transactional(readOnly = true)
    public com.servicedna.service.dto.ServiceMapDto getServiceMap(UUID organizationId, UUID userId) {
        organizationService.validateUserAccess(organizationId, userId);
        List<Service> services = serviceRepository.findByOrganizationId(organizationId);

        List<com.servicedna.service.dto.ServiceMapDto.ServiceNodeDto> nodes = services.stream()
                .map(s -> new com.servicedna.service.dto.ServiceMapDto.ServiceNodeDto(
                        s.getId(), s.getName(), s.getRegion(), s.getStatus()))
                .collect(Collectors.toList());

        List<com.servicedna.service.dto.ServiceMapDto.ServiceEdgeDto> edges = services.stream()
                .flatMap(s -> s.getDependencies().stream()
                        .map(d -> new com.servicedna.service.dto.ServiceMapDto.ServiceEdgeDto(s.getId(), d.getId())))
                .collect(Collectors.toList());

        return new com.servicedna.service.dto.ServiceMapDto(nodes, edges);
    }

    private String generateApiKey() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return "sdna_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private ServiceDto mapToDto(Service service) {
        List<UUID> dependencyIds = service.getDependencies().stream()
                .map(Service::getId)
                .collect(Collectors.toList());

        return new ServiceDto(
                service.getId(),
                service.getOrganization().getId(),
                service.getName(),
                service.getDescription(),
                service.getRepositoryUrl(),
                service.getRegion(),
                service.getStatus(),
                service.getApiKey(),
                dependencyIds,
                service.getCreatedAt(),
                service.getUpdatedAt()
        );
    }
}
