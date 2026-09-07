package com.servicedna.incident.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.incident.domain.Incident;
import com.servicedna.incident.domain.IncidentStatus;
import com.servicedna.incident.dto.CreateIncidentRequest;
import com.servicedna.incident.dto.IncidentDto;
import com.servicedna.incident.dto.UpdateIncidentStatusRequest;
import com.servicedna.incident.repository.IncidentRepository;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.user.domain.User;
import com.servicedna.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final ServiceRepository serviceRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationService organizationService;

    public IncidentService(
            IncidentRepository incidentRepository,
            ServiceRepository serviceRepository,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            OrganizationService organizationService
    ) {
        this.incidentRepository = incidentRepository;
        this.serviceRepository = serviceRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.organizationService = organizationService;
    }

    @Transactional
    public IncidentDto createIncident(UUID organizationId, CreateIncidentRequest request, UUID userId) {
        organizationService.validateUserAccess(organizationId, userId);

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORG_NOT_FOUND", "Organization not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        Incident incident = new Incident(
                UUID.randomUUID(),
                organization,
                user,
                request.title(),
                request.description(),
                request.severity()
        );

        if (request.affectedServiceIds() != null && !request.affectedServiceIds().isEmpty()) {
            Set<Service> affectedServices = new HashSet<>();
            for (UUID serviceId : request.affectedServiceIds()) {
                Service service = serviceRepository.findByOrganizationIdAndId(organizationId, serviceId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service " + serviceId + " not found"));
                affectedServices.add(service);
            }
            incident.setAffectedServices(affectedServices);
        }

        incident = incidentRepository.save(incident);
        return mapToDto(incident);
    }

    @Transactional(readOnly = true)
    public List<IncidentDto> getIncidents(UUID organizationId, UUID userId) {
        organizationService.validateUserAccess(organizationId, userId);
        return incidentRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public IncidentDto getIncident(UUID organizationId, UUID incidentId, UUID userId) {
        organizationService.validateUserAccess(organizationId, userId);

        Incident incident = incidentRepository.findByOrganizationIdAndId(organizationId, incidentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incident not found"));

        return mapToDto(incident);
    }

    @Transactional
    public IncidentDto updateIncidentStatus(UUID organizationId, UUID incidentId, UpdateIncidentStatusRequest request, UUID userId) {
        organizationService.validateUserAccess(organizationId, userId);

        Incident incident = incidentRepository.findByOrganizationIdAndId(organizationId, incidentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incident not found"));

        incident.setStatus(request.status());
        if (request.status() == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(OffsetDateTime.now());
        } else {
            incident.setResolvedAt(null);
        }

        incident = incidentRepository.save(incident);
        return mapToDto(incident);
    }

    private IncidentDto mapToDto(Incident incident) {
        List<UUID> affectedServiceIds = incident.getAffectedServices().stream()
                .map(Service::getId)
                .collect(Collectors.toList());

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
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }
}
