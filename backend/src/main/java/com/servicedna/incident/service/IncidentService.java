package com.servicedna.incident.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.common.mail.MailService;
import com.servicedna.dashboard.event.DashboardInvalidationEvent;
import com.servicedna.incident.domain.Incident;
import com.servicedna.incident.domain.IncidentPostMortem;
import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.incident.domain.IncidentStatus;
import com.servicedna.incident.dto.CreateIncidentRequest;
import com.servicedna.incident.dto.IncidentDto;
import com.servicedna.incident.dto.PostMortemDto;
import com.servicedna.incident.dto.UpdateIncidentStatusRequest;
import com.servicedna.incident.dto.UpsertPostMortemRequest;
import com.servicedna.incident.repository.IncidentPostMortemRepository;
import com.servicedna.incident.repository.IncidentRepository;
import com.servicedna.oncall.service.OnCallService;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.repository.OrganizationMemberRepository;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.user.domain.User;
import com.servicedna.user.repository.UserRepository;
import com.servicedna.webhook.service.WebhookNotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class IncidentService {

  private final IncidentRepository incidentRepository;
  private final IncidentPostMortemRepository postMortemRepository;
  private final ServiceRepository serviceRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationMemberRepository organizationMemberRepository;
  private final UserRepository userRepository;
  private final OrganizationService organizationService;
  private final OnCallService onCallService;
  private final MailService mailService;
  private final WebhookNotificationService webhookNotificationService;
  private final ApplicationEventPublisher eventPublisher;
  private final MeterRegistry meterRegistry;
  private final String frontendUrl;

  public IncidentService(
      IncidentRepository incidentRepository,
      IncidentPostMortemRepository postMortemRepository,
      ServiceRepository serviceRepository,
      OrganizationRepository organizationRepository,
      OrganizationMemberRepository organizationMemberRepository,
      UserRepository userRepository,
      OrganizationService organizationService,
      OnCallService onCallService,
      MailService mailService,
      WebhookNotificationService webhookNotificationService,
      ApplicationEventPublisher eventPublisher,
      MeterRegistry meterRegistry,
      @Value("${frontend.url}") String frontendUrl) {
    this.incidentRepository = incidentRepository;
    this.postMortemRepository = postMortemRepository;
    this.serviceRepository = serviceRepository;
    this.organizationRepository = organizationRepository;
    this.organizationMemberRepository = organizationMemberRepository;
    this.userRepository = userRepository;
    this.organizationService = organizationService;
    this.onCallService = onCallService;
    this.mailService = mailService;
    this.webhookNotificationService = webhookNotificationService;
    this.eventPublisher = eventPublisher;
    this.meterRegistry = meterRegistry;
    this.frontendUrl = frontendUrl;
  }

  @Transactional
  @org.springframework.cache.annotation.CacheEvict(
      value = "publicStatus",
      key = "#organizationId.toString()")
  public IncidentDto createIncident(
      UUID organizationId, CreateIncidentRequest request, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    Organization organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "ORG_NOT_FOUND", "Organization not found"));

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

    Incident incident =
        new Incident(
            UUID.randomUUID(),
            organization,
            user,
            request.title(),
            request.description(),
            request.severity());

    if (request.affectedServiceIds() != null && !request.affectedServiceIds().isEmpty()) {
      Set<Service> affectedServices = new HashSet<>();
      for (UUID serviceId : request.affectedServiceIds()) {
        Service service =
            serviceRepository
                .findByOrganizationIdAndId(organizationId, serviceId)
                .orElseThrow(
                    () ->
                        new ApiException(
                            HttpStatus.NOT_FOUND,
                            "SERVICE_NOT_FOUND",
                            "Service " + serviceId + " not found"));
        affectedServices.add(service);
      }
      incident.setAffectedServices(affectedServices);
    }

    incident = incidentRepository.save(incident);
    eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
    notifyOnCallIfSevere(incident);
    notifyOptedInMembers(incident, user);
    webhookNotificationService.notify(
        organizationId,
        "[" + incident.getSeverity() + "] New incident: " + incident.getTitle(),
        frontendUrl + "/incidents/" + incident.getId());
    return mapToDto(incident);
  }

  /**
   * Separate from on-call paging: this is opt-in visibility for members who aren't on call but
   * want to know about incidents regardless of severity (e.g. a manager), not an alerting path,
   * so it isn't gated to CRITICAL/MAJOR the way on-call paging is.
   */
  private void notifyOptedInMembers(Incident incident, User creator) {
    for (OrganizationMember member :
        organizationMemberRepository.findByOrganizationId(incident.getOrganization().getId())) {
      User user = member.getUser();
      if (!user.isNotifyOnNewIncident() || user.getId().equals(creator.getId())) {
        continue;
      }
      String link = frontendUrl + "/incidents/" + incident.getId();
      mailService.send(
          user.getEmail(),
          "[" + incident.getSeverity() + "] " + incident.getTitle(),
          "A new incident was reported in your organization:\n\n"
              + incident.getTitle()
              + "\n\n"
              + incident.getDescription()
              + "\n\n"
              + link);
    }
  }

  /**
   * Pages whoever's currently on call for CRITICAL/MAJOR incidents — without this, the on-call
   * rotation is purely informational and nobody actually gets notified when something serious
   * happens. MINOR/LOW incidents don't page to avoid alert fatigue.
   */
  private void notifyOnCallIfSevere(Incident incident) {
    if (incident.getSeverity() != IncidentSeverity.CRITICAL
        && incident.getSeverity() != IncidentSeverity.MAJOR) {
      return;
    }

    onCallService
        .getCurrentOnCallEmail(incident.getOrganization().getId())
        .ifPresent(
            email -> {
              String link = frontendUrl + "/incidents/" + incident.getId();
              mailService.send(
                  email,
                  "[" + incident.getSeverity() + "] " + incident.getTitle(),
                  "You're currently on call and a new "
                      + incident.getSeverity()
                      + " incident was just reported:\n\n"
                      + incident.getTitle()
                      + "\n\n"
                      + incident.getDescription()
                      + "\n\n"
                      + link);
            });
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

    Incident incident =
        incidentRepository
            .findByOrganizationIdAndId(organizationId, incidentId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incident not found"));

    return mapToDto(incident);
  }

  @Transactional
  @org.springframework.cache.annotation.CacheEvict(
      value = "publicStatus",
      key = "#organizationId.toString()")
  public IncidentDto updateIncidentStatus(
      UUID organizationId, UUID incidentId, UpdateIncidentStatusRequest request, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    Incident incident =
        incidentRepository
            .findByOrganizationIdAndId(organizationId, incidentId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incident not found"));

    incident.setStatus(request.status());
    if (request.status() == IncidentStatus.RESOLVED) {
      incident.setResolvedAt(OffsetDateTime.now());
    } else {
      incident.setResolvedAt(null);
    }

    incident = incidentRepository.save(incident);
    eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
    if (request.status() == IncidentStatus.RESOLVED) {
      webhookNotificationService.notify(
          organizationId,
          "Resolved: " + incident.getTitle(),
          frontendUrl + "/incidents/" + incident.getId());
    }
    return mapToDto(incident);
  }

  /** Any org member can acknowledge — this stops the escalation job from paging past them. */
  @Transactional
  public IncidentDto acknowledgeIncident(UUID organizationId, UUID incidentId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    Incident incident =
        incidentRepository
            .findByOrganizationIdAndId(organizationId, incidentId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incident not found"));

    if (incident.getAcknowledgedAt() == null) {
      incident.setAcknowledgedAt(OffsetDateTime.now());
      incident = incidentRepository.save(incident);
    }
    return mapToDto(incident);
  }

  @Transactional
  public PostMortemDto upsertPostMortem(
      UUID organizationId, UUID incidentId, UpsertPostMortemRequest request, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    Incident incident =
        incidentRepository
            .findByOrganizationIdAndId(organizationId, incidentId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incident not found"));

    if (incident.getStatus() != IncidentStatus.RESOLVED) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_STATE",
          "Post-mortem can only be added to resolved incidents");
    }

    Optional<IncidentPostMortem> existing = postMortemRepository.findByIncidentId(incidentId);
    IncidentPostMortem postMortem;
    if (existing.isPresent()) {
      postMortem = existing.get();
      postMortem.setRootCause(request.rootCause());
      postMortem.setTimeline(request.timeline());
      postMortem.setActionItems(request.actionItems());
    } else {
      postMortem =
          new IncidentPostMortem(
              UUID.randomUUID(),
              incident,
              request.rootCause(),
              request.timeline(),
              request.actionItems());
    }

    postMortem = postMortemRepository.save(postMortem);
    return mapToPostMortemDto(postMortem);
  }

  @Transactional(readOnly = true)
  public PostMortemDto getPostMortem(UUID organizationId, UUID incidentId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    Incident incident =
        incidentRepository
            .findByOrganizationIdAndId(organizationId, incidentId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incident not found"));

    IncidentPostMortem postMortem =
        postMortemRepository
            .findByIncidentId(incidentId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "POST_MORTEM_NOT_FOUND",
                        "Post-mortem not found for this incident"));

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
        postMortem.getUpdatedAt());
  }

  private IncidentDto mapToDto(Incident incident) {
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
