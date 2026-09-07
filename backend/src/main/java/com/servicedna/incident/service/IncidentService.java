package com.servicedna.incident.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.incident.domain.Incident;
import com.servicedna.incident.domain.IncidentPostMortem;
import com.servicedna.incident.domain.IncidentStatus;
import com.servicedna.incident.dto.CreateIncidentRequest;
import com.servicedna.incident.dto.IncidentDto;
import com.servicedna.incident.dto.PostMortemDto;
import com.servicedna.incident.dto.UpdateIncidentStatusRequest;
import com.servicedna.incident.dto.UpsertPostMortemRequest;
import com.servicedna.incident.repository.IncidentPostMortemRepository;
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
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import com.servicedna.dashboard.event.DashboardInvalidationEvent;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentPostMortemRepository postMortemRepository;
    private final ServiceRepository serviceRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationService organizationService;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    public IncidentService(
            IncidentRepository incidentRepository,
            IncidentPostMortemRepository postMortemRepository,
            ServiceRepository serviceRepository,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            OrganizationService organizationService,
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry
    ) {
        this.incidentRepository = incidentRepository;
        this.postMortemRepository = postMortemRepository;
        this.serviceRepository = serviceRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.organizationService = organizationService;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
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
        eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
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
        eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
        return mapToDto(incident);
    }

    @Transactional
    public PostMortemDto upsertPostMortem(UUID organizationId, UUID incidentId, UpsertPostMortemRequest request, UUID userId) {
        organizationService.validateUserAccess(organizationId, userId);

        Incident incident = incidentRepository.findByOrganizationIdAndId(organizationId, incidentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incident not found"));
                
        if (incident.getStatus() != IncidentStatus.RESOLVED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Post-mortem can only be added to resolved incidents");
        }

        Optional<IncidentPostMortem> existing = postMortemRepository.findByIncidentId(incidentId);
        IncidentPostMortem postMortem;
        if (existing.isPresent()) {
            postMortem = existing.get();
            postMortem.setRootCause(request.rootCause());
            postMortem.setTimeline(request.timeline());
            postMortem.setActionItems(request.actionItems());
        } else {
            postMortem = new IncidentPostMortem(UUID.randomUUID(), incident, request.rootCause(), request.timeline(), request.actionItems());
        }

        postMortem = postMortemRepository.save(postMortem);
        return mapToPostMortemDto(postMortem);
    }

    @Transactional(readOnly = true)
    public PostMortemDto getPostMortem(UUID organizationId, UUID incidentId, UUID userId) {
        organizationService.validateUserAccess(organizationId, userId);

        Incident incident = incidentRepository.findByOrganizationIdAndId(organizationId, incidentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incident not found"));

        IncidentPostMortem postMortem = postMortemRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_MORTEM_NOT_FOUND", "Post-mortem not found for this incident"));

        return mapToPostMortemDto(postMortem);
    }

    private PostMortemDto mapToPostMortemDto(IncidentPostMortem postMortem) {
        return new PostMortemDto(
                postMortem.getId(),
                postMortem.getIncident().getId(),
                postMortem.getRootCause(),
                postMortem.getTimeline(),
                postMortem.getActionItems(),
                postMortem.getCreatedAt(),
                postMortem.getUpdatedAt()
        );
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
