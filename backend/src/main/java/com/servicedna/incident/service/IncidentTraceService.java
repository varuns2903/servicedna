package com.servicedna.incident.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.common.exception.ApiException;
import com.servicedna.incident.domain.Incident;
import com.servicedna.incident.domain.IncidentEvent;
import com.servicedna.incident.domain.IncidentEventType;
import com.servicedna.incident.domain.IncidentTrace;
import com.servicedna.incident.repository.IncidentEventRepository;
import com.servicedna.incident.repository.IncidentRepository;
import com.servicedna.incident.repository.IncidentTraceRepository;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.testrun.TestRunDto;
import com.servicedna.testrun.TestRunViews;
import com.servicedna.traces.TraceDto;
import com.servicedna.traces.TraceQl;
import com.servicedna.traces.TraceQueryService;
import com.servicedna.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Traces during an incident: the failures in its window, and the ones attached as evidence. */
@Service
public class IncidentTraceService {

  /** How far before an incident was opened its failures are looked for. */
  static final Duration LEAD = Duration.ofMinutes(15);
  private static final int MAX_HOPS = 50;

  private final IncidentRepository incidents;
  private final IncidentTraceRepository attached;
  private final IncidentEventRepository events;
  private final UserRepository users;
  private final TraceQueryService traces;
  private final OrganizationService organizationService;
  private final ObjectMapper json;

  public IncidentTraceService(IncidentRepository incidents, IncidentTraceRepository attached, IncidentEventRepository events,
      UserRepository users, TraceQueryService traces, OrganizationService organizationService, ObjectMapper json) {
    this.incidents = incidents;
    this.attached = attached;
    this.events = events;
    this.users = users;
    this.traces = traces;
    this.organizationService = organizationService;
    this.json = json;
  }

  public record Attached(UUID id, String traceId, String note, String summary, List<TestRunDto.Hop> hops, String attachedBy, OffsetDateTime attachedAt) {}

  public record AttachRequest(String traceId, String note) {}

  /** Failing traces through the incident's affected services (any service, if none), from shortly before it opened until it resolved. */
  @Transactional(readOnly = true)
  public TraceDto.Explore failingTraces(UUID organizationId, UUID incidentId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    Incident incident = incident(organizationId, incidentId);
    String services = incident.getAffectedServices().stream()
        .map(s -> "resource.service.name = " + TraceQl.quote(s.getName()))
        .sorted()
        .collect(Collectors.joining(" || "));
    String query = services.isEmpty() ? "{ status = error }" : "{ status = error && (" + services + ") }";
    Instant from = incident.getCreatedAt().toInstant().minus(LEAD);
    Instant to = incident.getResolvedAt() != null ? incident.getResolvedAt().toInstant().plus(Duration.ofMinutes(5)) : Instant.now();
    return traces.explore(organizationId, null, query, from, to, 50, userId);
  }

  @Transactional
  public Attached attach(UUID organizationId, UUID incidentId, AttachRequest request, UUID userId) {
    requireResponder(organizationId, userId);
    Incident incident = incident(organizationId, incidentId);
    if (request.traceId() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRACE_ID", "Give the trace to attach.");
    }
    String traceId = request.traceId().trim().toLowerCase();
    if (attached.findByIncidentIdAndTraceId(incidentId, traceId).isPresent()) {
      throw new ApiException(HttpStatus.CONFLICT, "TRACE_ALREADY_ATTACHED", "That trace is already attached.");
    }
    List<TestRunDto.Hop> hops = TestRunViews.hops(traces.trace(organizationId, traceId));
    String summary = summary(traceId, hops);
    String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
    if (note != null && note.length() > 1000) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NOTE", "Notes are at most 1000 characters.");
    }
    IncidentTrace saved;
    try {
      saved = attached.save(new IncidentTrace(incidentId, traceId, note, summary,
          json.writeValueAsString(hops.size() > MAX_HOPS ? hops.subList(0, MAX_HOPS) : hops), userId));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
    events.save(new IncidentEvent(UUID.randomUUID(), incident, IncidentEventType.TRACE_ATTACHED, summary, users.findById(userId).orElse(null)));
    return view(saved);
  }

  @Transactional(readOnly = true)
  public List<Attached> list(UUID organizationId, UUID incidentId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    incident(organizationId, incidentId);
    return attached.findByIncidentIdOrderByAttachedAt(incidentId).stream().map(this::view).toList();
  }

  @Transactional
  public void detach(UUID organizationId, UUID incidentId, String traceId, UUID userId) {
    requireResponder(organizationId, userId);
    incident(organizationId, incidentId);
    attached.findByIncidentIdAndTraceId(incidentId, traceId.toLowerCase()).ifPresent(attached::delete);
  }

  /**
   * One line for the timeline: where the request came in and where it failed, e.g.
   * "Trace 4bf92f35: api-gateway POST /api/orders → failed at payment-service Charge (UNAVAILABLE)".
   */
  static String summary(String traceId, List<TestRunDto.Hop> hops) {
    StringBuilder s = new StringBuilder("Trace ").append(traceId, 0, Math.min(8, traceId.length()));
    if (hops.isEmpty()) {
      return s.append(" attached").toString();
    }
    TestRunDto.Hop entry = hops.get(0);
    s.append(": ").append(entry.service()).append(' ').append(entry.operation());
    // The deepest failing hop is where the failure started; its callers failed because of it.
    TestRunDto.Hop failed = null;
    for (TestRunDto.Hop h : hops) {
      if (h.error() || (h.httpStatus() != null && h.httpStatus() >= 500)) {
        failed = h;
      }
    }
    if (failed != null && failed != entry) {
      s.append(" → failed at ").append(failed.service()).append(' ').append(failed.operation());
    } else if (failed != null) {
      s.append(" failed");
    }
    if (failed != null) {
      String why = failed.statusMessage() != null ? failed.statusMessage() : failed.httpStatus() != null ? "HTTP " + failed.httpStatus() : null;
      if (why != null) {
        s.append(" (").append(why).append(')');
      }
    }
    return s.length() > 500 ? s.substring(0, 497) + "..." : s.toString();
  }

  private Attached view(IncidentTrace t) {
    List<TestRunDto.Hop> hops;
    try {
      hops = json.readValue(t.getHops(), new TypeReference<>() {});
    } catch (Exception e) {
      hops = List.of();
    }
    String by = t.getAttachedBy() == null ? null : users.findById(t.getAttachedBy()).map(u -> u.getEmail()).orElse(null);
    return new Attached(t.getId(), t.getTraceId(), t.getNote(), t.getSummary(), hops, by, t.getAttachedAt());
  }

  private Incident incident(UUID organizationId, UUID incidentId) {
    return incidents.findByOrganizationIdAndId(organizationId, incidentId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incident not found"));
  }

  private void requireResponder(UUID organizationId, UUID userId) {
    OrganizationMember member = organizationService.validateUserAccess(organizationId, userId);
    if (member.getRole() == OrganizationRole.VIEWER) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Viewers can't change incidents.");
    }
  }
}
