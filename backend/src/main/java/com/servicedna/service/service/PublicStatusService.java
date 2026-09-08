package com.servicedna.service.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.incident.domain.IncidentStatus;
import com.servicedna.incident.repository.IncidentRepository;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.dto.PublicStatusPageDto;
import com.servicedna.service.dto.PublicStatusPageDto.PublicIncidentDto;
import com.servicedna.service.dto.PublicStatusPageDto.PublicServiceDto;
import com.servicedna.service.repository.ServiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class PublicStatusService {

    private final OrganizationRepository organizationRepository;
    private final ServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;

    public PublicStatusService(OrganizationRepository organizationRepository, ServiceRepository serviceRepository, IncidentRepository incidentRepository) {
        this.organizationRepository = organizationRepository;
        this.serviceRepository = serviceRepository;
        this.incidentRepository = incidentRepository;
    }

    @Transactional(readOnly = true)
    public PublicStatusPageDto getPublicStatusPage(UUID organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORG_NOT_FOUND", "Organization not found"));

        List<com.servicedna.service.domain.Service> services = serviceRepository.findByOrganizationId(organizationId);
        List<PublicServiceDto> serviceDtos = services.stream()
                .map(s -> new PublicServiceDto(s.getId(), s.getName(), s.getDescription(), s.getStatus()))
                .collect(Collectors.toList());

        List<com.servicedna.incident.domain.Incident> incidents = incidentRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
        List<PublicIncidentDto> activeIncidentDtos = incidents.stream()
                .filter(i -> i.getStatus() != IncidentStatus.RESOLVED)
                .map(i -> new PublicIncidentDto(
                        i.getId(), i.getTitle(), i.getDescription(), i.getStatus(), i.getSeverity(), i.getCreatedAt(), i.getResolvedAt()
                ))
                .collect(Collectors.toList());

        String overallState = determineOverallState(serviceDtos);

        return new PublicStatusPageDto(
                organization.getId(),
                organization.getName(),
                overallState,
                serviceDtos,
                activeIncidentDtos
        );
    }

    private String determineOverallState(List<PublicServiceDto> services) {
        boolean anyDown = services.stream().anyMatch(s -> s.status() == ServiceStatus.DOWN);
        boolean anyDegraded = services.stream().anyMatch(s -> s.status() == ServiceStatus.DEGRADED);

        if (anyDown) return "MAJOR_OUTAGE";
        if (anyDegraded) return "PARTIAL_OUTAGE";
        return "ALL_SYSTEMS_OPERATIONAL";
    }
}
