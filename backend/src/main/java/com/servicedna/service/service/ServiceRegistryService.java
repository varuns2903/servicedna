package com.servicedna.service.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.Service;
import com.servicedna.service.dto.AddDependencyRequest;
import com.servicedna.service.dto.CreateServiceRequest;
import com.servicedna.service.dto.ServiceDto;
import com.servicedna.service.repository.ServiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class ServiceRegistryService {

    private final ServiceRepository serviceRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationService organizationService;
    private final SecureRandom secureRandom = new SecureRandom();

    public ServiceRegistryService(
            ServiceRepository serviceRepository,
            OrganizationRepository organizationRepository,
            OrganizationService organizationService
    ) {
        this.serviceRepository = serviceRepository;
        this.organizationRepository = organizationRepository;
        this.organizationService = organizationService;
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
                generateApiKey()
        );

        service = serviceRepository.save(service);
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

        return mapToDto(service);
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
                service.getStatus(),
                service.getApiKey(),
                dependencyIds,
                service.getCreatedAt(),
                service.getUpdatedAt()
        );
    }
}
