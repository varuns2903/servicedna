package com.servicedna.catalog.service;

import com.servicedna.catalog.domain.ServiceOperation;
import com.servicedna.catalog.dto.CatalogDto;
import com.servicedna.catalog.repository.ServiceOperationRepository;
import com.servicedna.common.exception.ApiException;
import com.servicedna.graph.domain.Protocol;
import com.servicedna.graph.repository.ObservedCallRepository;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.Service;
import com.servicedna.service.dto.AddDependencyRequest;
import com.servicedna.service.dto.TelemetryIdentity;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.service.service.ServiceRegistryService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class CatalogService {

  private final ServiceOperationRepository operationRepository;
  private final ObservedCallRepository observedCallRepository;
  private final ServiceRepository serviceRepository;
  private final ServiceRegistryService serviceRegistryService;
  private final OrganizationService organizationService;

  public CatalogService(
      ServiceOperationRepository operationRepository,
      ObservedCallRepository observedCallRepository,
      ServiceRepository serviceRepository,
      ServiceRegistryService serviceRegistryService,
      OrganizationService organizationService) {
    this.operationRepository = operationRepository;
    this.observedCallRepository = observedCallRepository;
    this.serviceRepository = serviceRepository;
    this.serviceRegistryService = serviceRegistryService;
    this.organizationService = organizationService;
  }

  /**
   * Applies what `sdna scan` found in a repository: registers the service if it's new, replaces its
   * spec-declared operations, and declares its dependencies on the services its configuration
   * names. Names that aren't registered services are reported back, not created.
   */
  @Transactional
  public CatalogDto.ScanResult applyScan(UUID organizationId, CatalogDto.ScanRequest request, UUID userId) {
    OrganizationMember member = organizationService.validateUserAccess(organizationId, userId);
    if (member.getRole() == OrganizationRole.VIEWER) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Viewers can't update service catalogs");
    }
    String environment = request.environment() == null || request.environment().isBlank() ? null : request.environment();
    List<Service> services = serviceRepository.findByOrganizationId(organizationId);
    // A scan without an environment applies to the service of that name, whatever its environment,
    // rather than registering an environment-less duplicate.
    Optional<Service> existing = byName(services, request.service(), environment);
    UUID serviceId =
        existing.isPresent() && (environment == null || environment.equals(existing.get().getEnvironment()))
            ? existing.get().getId()
            : serviceRegistryService
                .registerFromTelemetry(organizationId, new TelemetryIdentity(request.service(), environment, null, null, null, null))
                .orElseThrow(
                    () -> new ApiException(HttpStatus.FORBIDDEN, "PLAN_LIMIT_REACHED", "Your plan's service limit is reached."));

    Map<String, ServiceOperation> unique = new LinkedHashMap<>();
    if (request.operations() != null) {
      operationRepository.deleteByServiceId(serviceId);
      for (CatalogDto.OperationSpec op : request.operations()) {
        unique.putIfAbsent(
            op.protocol() + " " + op.name(),
            new ServiceOperation(serviceId, op.protocol(), op.name(), op.source(), op.description(), op.requestSchema()));
      }
      operationRepository.saveAll(unique.values());
    }

    List<String> added = new ArrayList<>();
    List<String> unknown = new ArrayList<>();
    for (String name : request.dependencies().stream().distinct().toList()) {
      Optional<Service> target = byName(services, name, environment).filter(s -> !s.getId().equals(serviceId));
      if (target.isPresent()) {
        serviceRegistryService.addDependency(organizationId, serviceId, new AddDependencyRequest(target.get().getId()), userId);
        added.add(name);
      } else if (!name.equalsIgnoreCase(request.service())) {
        unknown.add(name);
      }
    }
    return new CatalogDto.ScanResult(serviceId, unique.size(), added, unknown);
  }

  /** The service's operations from its specs, merged with the ones traffic shows callers using. */
  @Transactional(readOnly = true)
  public List<CatalogDto.Operation> operations(UUID organizationId, UUID serviceId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    serviceRepository
        .findByOrganizationIdAndId(organizationId, serviceId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));

    Map<String, CatalogDto.Operation> result = new LinkedHashMap<>();
    for (ServiceOperation op : operationRepository.findByServiceIdOrderByName(serviceId)) {
      result.put(key(op.getProtocol(), op.getName()),
          new CatalogDto.Operation(op.getProtocol(), op.getName(), op.getSource(), op.getDescription(), op.getRequestSchema(), false, 0));
    }
    for (Object[] row : observedCallRepository.findTargetOperationsSince(serviceId, OffsetDateTime.now().minusHours(24))) {
      Protocol protocol = (Protocol) row[0];
      String name = (String) row[1];
      long calls = ((Number) row[2]).longValue();
      if (name == null || name.isBlank()) {
        continue;
      }
      CatalogDto.Operation spec = result.get(key(protocol, name));
      result.put(key(protocol, name),
          spec != null
              ? new CatalogDto.Operation(protocol, name, spec.source(), spec.description(), spec.requestSchema(), true, calls)
              : new CatalogDto.Operation(protocol, name, "TRAFFIC", null, null, true, calls));
    }
    return new ArrayList<>(result.values());
  }

  private static Optional<Service> byName(List<Service> services, String name, String environment) {
    List<Service> matches = services.stream().filter(s -> s.getName().equalsIgnoreCase(name)).toList();
    return matches.stream()
        .filter(s -> Objects.equals(s.getEnvironment(), environment))
        .findFirst()
        .or(() -> matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty());
  }

  private static String key(Protocol protocol, String name) {
    return protocol + " " + name;
  }
}
