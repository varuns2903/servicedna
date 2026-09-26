package com.servicedna.incident.controller;

import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.incident.service.IncidentTraceService;
import com.servicedna.traces.TraceDto;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/incidents/{incidentId}")
public class IncidentTraceController {

  private final IncidentTraceService service;

  public IncidentTraceController(IncidentTraceService service) {
    this.service = service;
  }

  /** Failing traces through the affected services during the incident. */
  @GetMapping("/failing-traces")
  public ResponseEntity<TraceDto.Explore> failing(
      @PathVariable UUID orgId, @PathVariable UUID incidentId, @AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(service.failingTraces(orgId, incidentId, user.getUser().getId()));
  }

  @GetMapping("/traces")
  public ResponseEntity<List<IncidentTraceService.Attached>> attached(
      @PathVariable UUID orgId, @PathVariable UUID incidentId, @AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(service.list(orgId, incidentId, user.getUser().getId()));
  }

  @PostMapping("/traces")
  public ResponseEntity<IncidentTraceService.Attached> attach(
      @PathVariable UUID orgId, @PathVariable UUID incidentId, @RequestBody IncidentTraceService.AttachRequest request,
      @AuthenticationPrincipal CustomUserDetails user) {
    return new ResponseEntity<>(service.attach(orgId, incidentId, request, user.getUser().getId()), HttpStatus.CREATED);
  }

  @DeleteMapping("/traces/{traceId}")
  public ResponseEntity<Void> detach(
      @PathVariable UUID orgId, @PathVariable UUID incidentId, @PathVariable String traceId, @AuthenticationPrincipal CustomUserDetails user) {
    service.detach(orgId, incidentId, traceId, user.getUser().getId());
    return ResponseEntity.noContent().build();
  }
}
