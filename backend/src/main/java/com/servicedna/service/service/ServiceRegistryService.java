package com.servicedna.service.service;

import com.servicedna.billing.service.PlanLimitService;
import java.util.Optional;
import com.servicedna.service.dto.TelemetryIdentity;
import com.servicedna.service.domain.ServiceSource;
import com.servicedna.alert.event.ServiceStatusChangedEvent;
import com.servicedna.alert.service.AlertEventPublisher;
import com.servicedna.common.exception.ApiException;
import com.servicedna.dashboard.event.DashboardInvalidationEvent;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.event.AuditLogEvent;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.Service;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.service.dto.AddDependencyRequest;
import com.servicedna.service.dto.CreateServiceRequest;
import com.servicedna.service.dto.ServiceDto;
import com.servicedna.service.dto.UpdateServiceRequest;
import com.servicedna.service.dto.UpdateServiceStatusRequest;
import com.servicedna.service.repository.ServiceRepository;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class ServiceRegistryService {

  private final ServiceRepository serviceRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationService organizationService;
  private final AlertEventPublisher alertEventPublisher;
  private final ApplicationEventPublisher eventPublisher;
  private final SecureRandom secureRandom = new SecureRandom();

  private final PlanLimitService planLimitService;
  private final org.springframework.cache.CacheManager cacheManager;

  public ServiceRegistryService(
      ServiceRepository serviceRepository,
      OrganizationRepository organizationRepository,
      OrganizationService organizationService,
      @Lazy AlertEventPublisher alertEventPublisher,
      ApplicationEventPublisher eventPublisher,
      PlanLimitService planLimitService,
      org.springframework.cache.CacheManager cacheManager) {
    this.serviceRepository = serviceRepository;
    this.organizationRepository = organizationRepository;
    this.organizationService = organizationService;
    this.alertEventPublisher = alertEventPublisher;
    this.eventPublisher = eventPublisher;
    this.planLimitService = planLimitService;
    this.cacheManager = cacheManager;
  }

  @Transactional
  @org.springframework.cache.annotation.CacheEvict(
      value = {"services", "publicStatus"},
      allEntries = true)
  public ServiceDto createService(UUID organizationId, CreateServiceRequest request, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    planLimitService.checkCanAddService(organizationId);

    if (serviceRepository.existsByOrganizationIdAndName(organizationId, request.name())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "SERVICE_EXISTS",
          "Service with this name already exists in the organization");
    }

    Organization organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "ORG_NOT_FOUND", "Organization not found"));

    Service service =
        new Service(
            UUID.randomUUID(),
            organization,
            request.name(),
            request.description(),
            request.repositoryUrl(),
            request.region(),
            generateApiKey(),
            request.healthCheckUrl());

    service = serviceRepository.save(service);
    eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
    eventPublisher.publishEvent(
        new AuditLogEvent(
            organizationId,
            userId,
            "CREATE_SERVICE",
            "Service",
            service.getId().toString(),
            "Created service " + service.getName(),
            null));
    return mapToDtoWithApiKey(service);
  }

  /**
   * Finds or creates the service a piece of telemetry comes from, and refreshes what its telemetry
   * says about it. A service is identified by name within an environment; telemetry naming an
   * environment adopts a service of the same name registered without one, rather than creating a
   * duplicate.
   *
   * @return the service's id, or empty if the organization's plan doesn't allow another service
   */
  @Transactional
  public Optional<UUID> registerFromTelemetry(UUID organizationId, TelemetryIdentity identity) {
    Optional<Service> existing =
        serviceRepository.findByOrganizationIdAndNameAndEnvironment(
            organizationId, identity.name(), identity.environment());
    if (existing.isEmpty() && identity.environment() != null) {
      existing =
          serviceRepository.findByOrganizationIdAndNameAndEnvironmentIsNull(organizationId, identity.name());
      existing.ifPresent(service -> service.setEnvironment(identity.environment()));
    }

    Service service;
    boolean changed;
    if (existing.isPresent()) {
      service = existing.get();
      changed = applyTelemetry(service, identity);
    } else {
      if (!planLimitService.canAddService(organizationId)) {
        return Optional.empty();
      }
      Organization organization =
          organizationRepository
              .findById(organizationId)
              .orElseThrow(
                  () -> new ApiException(HttpStatus.NOT_FOUND, "ORG_NOT_FOUND", "Organization not found"));
      service =
          new Service(
              UUID.randomUUID(),
              organization,
              identity.name(),
              null,
              null,
              identity.region() != null ? identity.region() : "global",
              generateApiKey(),
              identity.healthCheckUrl());
      service.setEnvironment(identity.environment());
      service.setSource(ServiceSource.TELEMETRY);
      applyTelemetry(service, identity);
      changed = true;
      eventPublisher.publishEvent(
          new AuditLogEvent(
              organizationId,
              null,
              "AUTO_REGISTER_SERVICE",
              "Service",
              service.getId().toString(),
              "Registered " + identity.name()
                  + (identity.environment() != null ? " (" + identity.environment() + ")" : "")
                  + " from its telemetry",
              null));
    }

    service.setLastTelemetryAt(OffsetDateTime.now());
    service = serviceRepository.saveAndFlush(service);
    if (changed) {
      evictServiceCachesAfterCommit();
      eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
    }
    return Optional.of(service.getId());
  }

  /** Copies what telemetry reports onto the service; a configured health URL is never replaced. */
  private static boolean applyTelemetry(Service service, TelemetryIdentity identity) {
    boolean changed = false;
    if (identity.language() != null && !identity.language().equals(service.getLanguage())) {
      service.setLanguage(identity.language());
      changed = true;
    }
    if (identity.version() != null && !identity.version().equals(service.getVersion())) {
      service.setVersion(identity.version());
      changed = true;
    }
    if (identity.healthCheckUrl() != null && service.getHealthCheckUrl() == null) {
      service.setHealthCheckUrl(identity.healthCheckUrl());
      changed = true;
    }
    return changed;
  }

  private void evictServiceCachesAfterCommit() {
    Runnable evict =
        () -> {
          for (String name : new String[] {"services", "publicStatus"}) {
            org.springframework.cache.Cache cache = cacheManager.getCache(name);
            if (cache != null) {
              cache.clear();
            }
          }
        };
    if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
      org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
          new org.springframework.transaction.support.TransactionSynchronization() {
            @Override
            public void afterCommit() {
              evict.run();
            }
          });
    } else {
      evict.run();
    }
  }

  @Transactional(readOnly = true)
  @org.springframework.cache.annotation.Cacheable(
      value = "services",
      key = "#organizationId.toString() + '_list'")
  public List<ServiceDto> getServices(UUID organizationId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    return serviceRepository.findByOrganizationId(organizationId).stream()
        .map(this::mapToDto)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  @org.springframework.cache.annotation.Cacheable(value = "services", key = "#serviceId.toString()")
  public ServiceDto getService(UUID organizationId, UUID serviceId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    Service service =
        serviceRepository
            .findByOrganizationIdAndId(organizationId, serviceId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));

    eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
    return mapToDto(service);
  }

  @Transactional
  @org.springframework.cache.annotation.CacheEvict(
      value = {"services", "publicStatus"},
      allEntries = true)
  public ServiceDto updateServiceStatus(
      UUID organizationId, UUID serviceId, UpdateServiceStatusRequest request, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    Service service =
        serviceRepository
            .findByOrganizationIdAndId(organizationId, serviceId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));

    ServiceStatus oldStatus = service.getStatus();
    ServiceStatus newStatus = request.status();

    service.setStatus(newStatus);
    service = serviceRepository.save(service);

    if (oldStatus != newStatus && alertEventPublisher != null) {
      alertEventPublisher.publishStatusChangedEvent(
          new ServiceStatusChangedEvent(
              service.getId(),
              service.getOrganization().getId(),
              oldStatus,
              newStatus,
              OffsetDateTime.now()));
    }

    eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
    eventPublisher.publishEvent(
        new AuditLogEvent(
            organizationId,
            userId,
            "UPDATE_SERVICE_STATUS",
            "Service",
            service.getId().toString(),
            "Updated status from " + oldStatus + " to " + newStatus,
            null));
    return mapToDto(service);
  }

  @Transactional
  @org.springframework.cache.annotation.CacheEvict(value = "services", allEntries = true)
  public ServiceDto addDependency(
      UUID organizationId, UUID serviceId, AddDependencyRequest request, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    Service service =
        serviceRepository
            .findByOrganizationIdAndId(organizationId, serviceId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));

    Service dependency =
        serviceRepository
            .findByOrganizationIdAndId(organizationId, request.dependsOnServiceId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "DEPENDENCY_NOT_FOUND",
                        "Dependency service not found"));

    if (service.getId().equals(dependency.getId())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_DEPENDENCY", "A service cannot depend on itself");
    }

    service.getDependencies().add(dependency);
    service = serviceRepository.save(service);

    eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
    return mapToDto(service);
  }

  @Transactional
  @org.springframework.cache.annotation.CacheEvict(
      value = {"services", "publicStatus"},
      allEntries = true)
  public ServiceDto updateService(
      UUID organizationId, UUID serviceId, UpdateServiceRequest request, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    Service service =
        serviceRepository
            .findByOrganizationIdAndId(organizationId, serviceId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));

    if (!service.getName().equals(request.name())
        && serviceRepository.existsByOrganizationIdAndName(organizationId, request.name())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "SERVICE_EXISTS",
          "Service with this name already exists in the organization");
    }

    service.setName(request.name());
    service.setDescription(request.description());
    service.setRepositoryUrl(request.repositoryUrl());
    service.setRegion(request.region() != null ? request.region() : service.getRegion());
    service.setHealthCheckUrl(request.healthCheckUrl());
    if (request.sloTargetPercentage() != null) {
      service.setSloTargetPercentage(request.sloTargetPercentage());
    }

    service = serviceRepository.save(service);
    eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
    eventPublisher.publishEvent(
        new AuditLogEvent(
            organizationId,
            userId,
            "UPDATE_SERVICE",
            "Service",
            service.getId().toString(),
            "Updated service " + service.getName(),
            null));
    return mapToDto(service);
  }

  @Transactional
  @org.springframework.cache.annotation.CacheEvict(
      value = {"services", "publicStatus"},
      allEntries = true)
  public void deleteService(UUID organizationId, UUID serviceId, UUID userId) {
    OrganizationMember requester = organizationService.validateUserAccess(organizationId, userId);
    if (requester.getRole() != OrganizationRole.OWNER
        && requester.getRole() != OrganizationRole.ADMIN) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Only owners and admins can delete a service");
    }

    Service service =
        serviceRepository
            .findByOrganizationIdAndId(organizationId, serviceId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));

    serviceRepository.delete(service);
    eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
    eventPublisher.publishEvent(
        new AuditLogEvent(
            organizationId,
            userId,
            "DELETE_SERVICE",
            "Service",
            serviceId.toString(),
            "Deleted service " + service.getName(),
            null));
  }

  @Transactional
  @org.springframework.cache.annotation.CacheEvict(
      value = {"services", "publicStatus"},
      allEntries = true)
  public ServiceDto regenerateApiKey(UUID organizationId, UUID serviceId, UUID userId) {
    OrganizationMember requester = organizationService.validateUserAccess(organizationId, userId);
    if (requester.getRole() != OrganizationRole.OWNER
        && requester.getRole() != OrganizationRole.ADMIN) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "ACCESS_DENIED",
          "Only owners and admins can regenerate a service's API key");
    }

    Service service =
        serviceRepository
            .findByOrganizationIdAndId(organizationId, serviceId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));

    service.setApiKey(generateApiKey());
    service = serviceRepository.save(service);

    eventPublisher.publishEvent(
        new AuditLogEvent(
            organizationId,
            userId,
            "REGENERATE_SERVICE_API_KEY",
            "Service",
            service.getId().toString(),
            "Regenerated API key for service " + service.getName(),
            null));
    return mapToDtoWithApiKey(service);
  }

  @Transactional(readOnly = true)
  public com.servicedna.service.dto.ServiceMapDto getServiceMap(UUID organizationId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    List<Service> services = serviceRepository.findByOrganizationId(organizationId);

    List<com.servicedna.service.dto.ServiceMapDto.ServiceNodeDto> nodes =
        services.stream()
            .map(
                s ->
                    new com.servicedna.service.dto.ServiceMapDto.ServiceNodeDto(
                        s.getId(), s.getName(), s.getRegion(), s.getStatus()))
            .collect(Collectors.toList());

    List<com.servicedna.service.dto.ServiceMapDto.ServiceEdgeDto> edges =
        services.stream()
            .flatMap(
                s ->
                    s.getDependencies().stream()
                        .map(
                            d ->
                                new com.servicedna.service.dto.ServiceMapDto.ServiceEdgeDto(
                                    s.getId(), d.getId())))
            .collect(Collectors.toList());

    return new com.servicedna.service.dto.ServiceMapDto(nodes, edges);
  }

  private String generateApiKey() {
    byte[] randomBytes = new byte[32];
    secureRandom.nextBytes(randomBytes);
    return "sdna_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }

  /**
   * Redacts the API key. Used everywhere except immediately after creation — the key is a
   * bearer credential for the service's telemetry endpoint and must not be re-exposed to every
   * org member on every list/get call.
   */
  private ServiceDto mapToDto(Service service) {
    List<UUID> dependencyIds =
        service.getDependencies().stream().map(Service::getId).collect(Collectors.toList());

    return new ServiceDto(
        service.getId(),
        service.getOrganization().getId(),
        service.getName(),
        service.getDescription(),
        service.getRepositoryUrl(),
        service.getRegion(),
        service.getHealthCheckUrl(),
        service.getSloTargetPercentage(),
        service.getStatus(),
        null,
        dependencyIds,
        service.getEnvironment(),
        service.getLanguage(),
        service.getVersion(),
        service.getSource(),
        service.getLastTelemetryAt(),
        service.getCreatedAt(),
        service.getUpdatedAt());
  }

  private ServiceDto mapToDtoWithApiKey(Service service) {
    List<UUID> dependencyIds =
        service.getDependencies().stream().map(Service::getId).collect(Collectors.toList());

    return new ServiceDto(
        service.getId(),
        service.getOrganization().getId(),
        service.getName(),
        service.getDescription(),
        service.getRepositoryUrl(),
        service.getRegion(),
        service.getHealthCheckUrl(),
        service.getSloTargetPercentage(),
        service.getStatus(),
        service.getApiKey(),
        dependencyIds,
        service.getEnvironment(),
        service.getLanguage(),
        service.getVersion(),
        service.getSource(),
        service.getLastTelemetryAt(),
        service.getCreatedAt(),
        service.getUpdatedAt());
  }
}
