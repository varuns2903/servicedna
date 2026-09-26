package com.servicedna.incident.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.common.mail.MailService;
import com.servicedna.dashboard.event.DashboardInvalidationEvent;
import com.servicedna.incident.domain.Incident;
import com.servicedna.incident.domain.IncidentEvent;
import com.servicedna.incident.domain.IncidentEventType;
import com.servicedna.incident.domain.IncidentPostMortem;
import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.incident.domain.IncidentStatus;
import com.servicedna.incident.dto.CreateIncidentRequest;
import com.servicedna.incident.dto.IncidentDto;
import com.servicedna.incident.dto.IncidentEventDto;
import com.servicedna.incident.dto.PostMortemDto;
import com.servicedna.incident.dto.UpdateIncidentStatusRequest;
import com.servicedna.incident.dto.UpsertPostMortemRequest;
import com.servicedna.incident.repository.IncidentEventRepository;
import com.servicedna.incident.repository.IncidentPostMortemRepository;
import com.servicedna.incident.repository.IncidentRepository;
import com.servicedna.oncall.service.OnCallService;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.repository.OrganizationMemberRepository;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.Service;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.service.service.DependencyGraphService;
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
  private final IncidentEventRepository incidentEventRepository;
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
  private final DependencyGraphService dependencyGraphService;
  private final String frontendUrl;

  public IncidentService(
      IncidentRepository incidentRepository,
      IncidentEventRepository incidentEventRepository,
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
      @Value("${frontend.url}") String frontendUrl,
      DependencyGraphService dependencyGraphService) {
    this.incidentRepository = incidentRepository;
    this.incidentEventRepository = incidentEventRepository;
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
    this.dependencyGraphService = dependencyGraphService;
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
    recordEvent(incident, IncidentEventType.CREATED, "Incident reported", user);
    announceNewIncident(incident, user);
    return mapToDto(incident);
  }

  /**
   * Opens an incident for a service whose alert rule asked for one, or folds the alert into an
   * existing one, so one outage produces one incident:
   *
   * <ul>
   *   <li>If the service is already affected by an open alert incident, that incident is updated.
   *   <li>Otherwise, if a service it depends on (or that depends on it) is affected by one, the
   *       service joins that incident — a cascading failure shares its root cause's incident,
   *       whichever of the two reported first.
   *   <li>Otherwise a new incident opens.
   * </ul>
   *
   * Severity only ever rises, and on-call is paged when it reaches CRITICAL/MAJOR.
   */
  @Transactional
  @org.springframework.cache.annotation.CacheEvict(
      value = "publicStatus",
      key = "#organizationId.toString()")
  public IncidentDto openOrUpdateAlertIncident(
      UUID organizationId,
      UUID serviceId,
      String serviceName,
      ServiceStatus oldStatus,
      ServiceStatus newStatus,
      IncidentSeverity severity) {
    String change = serviceName + " changed status from " + oldStatus + " to " + newStatus;

    Optional<Incident> affecting = openAlertIncidentAffecting(serviceId);
    if (affecting.isPresent()) {
      Incident incident = affecting.get();
      if (serviceId.equals(incident.getTriggeredByServiceId())) {
        incident.setTitle(serviceName + " is " + newStatus);
      }
      recordEvent(incident, IncidentEventType.ALERT_TRIGGERED, change, null);
      return saveAlertUpdate(incident, severity, organizationId);
    }

    Service service = findService(organizationId, serviceId);
    Optional<RelatedIncident> related = findRelatedAlertIncident(organizationId, service);
    if (related.isPresent()) {
      Incident incident = related.get().incident();
      incident.getAffectedServices().add(service);
      recordEvent(
          incident,
          IncidentEventType.ALERT_TRIGGERED,
          change + " (" + related.get().relation() + ")",
          null);
      // The incident's current root depends on the newcomer, so the newcomer is the likelier
      // cause: title the incident after it.
      if (related.get().isDependencyOfRoot()) {
        incident.setTriggeredByServiceId(serviceId);
        incident.setTitle(serviceName + " is " + newStatus);
        recordEvent(
            incident,
            IncidentEventType.ALERT_TRIGGERED,
            "Likely root cause is now " + serviceName,
            null);
      }
      return saveAlertUpdate(incident, severity, organizationId);
    }

    Organization organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "ORG_NOT_FOUND", "Organization not found"));
    Incident incident =
        new Incident(
            UUID.randomUUID(),
            organization,
            null,
            serviceName + " is " + newStatus,
            "Opened automatically by an alert rule: " + change + ".",
            severity);
    incident.setTriggeredByServiceId(serviceId);
    incident.setAffectedServices(new HashSet<>(Set.of(service)));
    incident = incidentRepository.save(incident);
    recordEvent(incident, IncidentEventType.CREATED, "Opened automatically: " + change, null);
    announceNewIncident(incident, null);
    return mapToDto(incident);
  }

  /**
   * Records a service's recovery on the alert incident affecting it, and resolves the incident
   * once every affected service is healthy again.
   */
  @Transactional
  @org.springframework.cache.annotation.CacheEvict(
      value = "publicStatus",
      key = "#organizationId.toString()")
  public Optional<IncidentDto> resolveAlertIncident(
      UUID organizationId, UUID serviceId, String serviceName) {
    return openAlertIncidentAffecting(serviceId)
        .map(
            incident -> {
              // The recovering service's own row may not be refreshed in this persistence
              // context yet; the event that brought us here already says it's healthy.
              List<String> stillUnhealthy =
                  incident.getAffectedServices().stream()
                      .filter(s -> !s.getId().equals(serviceId))
                      .filter(s -> s.getStatus() != ServiceStatus.HEALTHY)
                      .map(Service::getName)
                      .sorted()
                      .toList();
              if (!stillUnhealthy.isEmpty()) {
                recordEvent(
                    incident,
                    IncidentEventType.SERVICE_RECOVERED,
                    serviceName + " recovered; still waiting on " + String.join(", ", stillUnhealthy),
                    null);
                eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
                return mapToDto(incident);
              }

              incident.setStatus(IncidentStatus.RESOLVED);
              incident.setResolvedAt(OffsetDateTime.now());
              incident = incidentRepository.save(incident);
              recordEvent(
                  incident,
                  IncidentEventType.STATUS_CHANGED,
                  incident.getAffectedServices().size() == 1
                      ? "Resolved automatically: " + serviceName + " recovered"
                      : "Resolved automatically: all affected services recovered",
                  null);
              eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
              webhookNotificationService.notify(
                  organizationId,
                  "Resolved: " + incident.getTitle(),
                  frontendUrl + "/incidents/" + incident.getId());
              return mapToDto(incident);
            });
  }

  private Optional<Incident> openAlertIncidentAffecting(UUID serviceId) {
    return incidentRepository
        .findOpenAlertIncidentsAffecting(serviceId, IncidentStatus.RESOLVED)
        .stream()
        .findFirst();
  }

  private record RelatedIncident(Incident incident, String relation, boolean isDependencyOfRoot) {}

  /** The newest open alert incident affecting something this service depends on, or vice versa. */
  private Optional<RelatedIncident> findRelatedAlertIncident(UUID organizationId, Service service) {
    DependencyGraphService.Relations relations =
        dependencyGraphService.relationsOf(organizationId, service.getId());
    List<Incident> open =
        incidentRepository
            .findByOrganizationIdAndTriggeredByServiceIdIsNotNullAndStatusNotOrderByCreatedAtDesc(
                organizationId, IncidentStatus.RESOLVED);
    for (Incident incident : open) {
      for (Service affected : incident.getAffectedServices()) {
        if (relations.dependencies().contains(affected.getId())) {
          return Optional.of(
              new RelatedIncident(incident, "depends on " + affected.getName(), false));
        }
        if (relations.dependents().contains(affected.getId())) {
          boolean rootDependsOnIt =
              relations.dependents().contains(incident.getTriggeredByServiceId());
          return Optional.of(
              new RelatedIncident(
                  incident, affected.getName() + " depends on it", rootDependsOnIt));
        }
      }
    }
    return Optional.empty();
  }

  private IncidentDto saveAlertUpdate(
      Incident incident, IncidentSeverity severity, UUID organizationId) {
    // IncidentSeverity is declared most severe first.
    if (severity.ordinal() < incident.getSeverity().ordinal()) {
      IncidentSeverity previous = incident.getSeverity();
      incident.setSeverity(severity);
      recordEvent(
          incident,
          IncidentEventType.SEVERITY_CHANGED,
          "Severity raised from " + previous + " to " + severity,
          null);
      notifyOnCallIfSevere(incident);
    }
    incident = incidentRepository.save(incident);
    eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
    return mapToDto(incident);
  }

  private Service findService(UUID organizationId, UUID serviceId) {
    return serviceRepository
        .findByOrganizationIdAndId(organizationId, serviceId)
        .orElseThrow(
            () -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));
  }

  /** Fan-out for a newly opened incident; {@code creator} is null when an alert opened it. */
  private void announceNewIncident(Incident incident, User creator) {
    UUID organizationId = incident.getOrganization().getId();
    eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
    notifyOnCallIfSevere(incident);
    notifyOptedInMembers(incident, creator);
    webhookNotificationService.notify(
        organizationId,
        "[" + incident.getSeverity() + "] New incident: " + incident.getTitle(),
        frontendUrl + "/incidents/" + incident.getId());
  }

  private void recordEvent(Incident incident, IncidentEventType type, String message, User actor) {
    incidentEventRepository.save(
        new IncidentEvent(UUID.randomUUID(), incident, type, message, actor));
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
      if (!user.isNotifyOnNewIncident() || (creator != null && user.getId().equals(creator.getId()))) {
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
    recordEvent(
        incident,
        IncidentEventType.STATUS_CHANGED,
        "Status changed to " + request.status(),
        userRepository.findById(userId).orElse(null));
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
      recordEvent(
          incident,
          IncidentEventType.ACKNOWLEDGED,
          "Incident acknowledged",
          userRepository.findById(userId).orElse(null));
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
    recordEvent(
        incident,
        IncidentEventType.POST_MORTEM_UPDATED,
        existing.isPresent() ? "Post-mortem updated" : "Post-mortem added",
        userRepository.findById(userId).orElse(null));
    return mapToPostMortemDto(postMortem);
  }

  @Transactional(readOnly = true)
  public List<IncidentEventDto> getIncidentEvents(UUID organizationId, UUID incidentId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);

    incidentRepository
        .findByOrganizationIdAndId(organizationId, incidentId)
        .orElseThrow(
            () ->
                new ApiException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incident not found"));

    return incidentEventRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId).stream()
        .map(this::mapEventToDto)
        .collect(Collectors.toList());
  }

  private IncidentEventDto mapEventToDto(IncidentEvent event) {
    return new IncidentEventDto(
        event.getId(),
        event.getEventType(),
        event.getMessage(),
        event.getActor() != null ? event.getActor().getEmail() : null,
        event.getCreatedAt());
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
        incident.getCreatedBy() != null ? incident.getCreatedBy().getId() : null,
        incident.getTriggeredByServiceId(),
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
