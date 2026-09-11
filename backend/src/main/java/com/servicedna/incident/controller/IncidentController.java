package com.servicedna.incident.controller;

import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.incident.dto.CreateIncidentRequest;
import com.servicedna.incident.dto.IncidentDto;
import com.servicedna.incident.dto.IncidentEventDto;
import com.servicedna.incident.dto.PostMortemDto;
import com.servicedna.incident.dto.UpdateIncidentStatusRequest;
import com.servicedna.incident.dto.UpsertPostMortemRequest;
import com.servicedna.incident.service.IncidentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/incidents")
public class IncidentController {

  private final IncidentService incidentService;

  public IncidentController(IncidentService incidentService) {
    this.incidentService = incidentService;
  }

  @PostMapping
  public ResponseEntity<IncidentDto> createIncident(
      @PathVariable UUID orgId,
      @Valid @RequestBody CreateIncidentRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return new ResponseEntity<>(
        incidentService.createIncident(orgId, request, userDetails.getUser().getId()),
        HttpStatus.CREATED);
  }

  @GetMapping
  public ResponseEntity<List<IncidentDto>> getIncidents(
      @PathVariable UUID orgId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(incidentService.getIncidents(orgId, userDetails.getUser().getId()));
  }

  @GetMapping("/{incidentId}")
  public ResponseEntity<IncidentDto> getIncident(
      @PathVariable UUID orgId,
      @PathVariable UUID incidentId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        incidentService.getIncident(orgId, incidentId, userDetails.getUser().getId()));
  }

  @PatchMapping("/{incidentId}/status")
  public ResponseEntity<IncidentDto> updateIncidentStatus(
      @PathVariable UUID orgId,
      @PathVariable UUID incidentId,
      @Valid @RequestBody UpdateIncidentStatusRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        incidentService.updateIncidentStatus(
            orgId, incidentId, request, userDetails.getUser().getId()));
  }

  @PostMapping("/{incidentId}/acknowledge")
  public ResponseEntity<IncidentDto> acknowledgeIncident(
      @PathVariable UUID orgId,
      @PathVariable UUID incidentId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        incidentService.acknowledgeIncident(orgId, incidentId, userDetails.getUser().getId()));
  }

  @PutMapping("/{incidentId}/post-mortem")
  public ResponseEntity<PostMortemDto> upsertPostMortem(
      @PathVariable UUID orgId,
      @PathVariable UUID incidentId,
      @Valid @RequestBody UpsertPostMortemRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        incidentService.upsertPostMortem(
            orgId, incidentId, request, userDetails.getUser().getId()));
  }

  @GetMapping("/{incidentId}/post-mortem")
  public ResponseEntity<PostMortemDto> getPostMortem(
      @PathVariable UUID orgId,
      @PathVariable UUID incidentId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        incidentService.getPostMortem(orgId, incidentId, userDetails.getUser().getId()));
  }

  @GetMapping("/{incidentId}/events")
  public ResponseEntity<List<IncidentEventDto>> getIncidentEvents(
      @PathVariable UUID orgId,
      @PathVariable UUID incidentId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        incidentService.getIncidentEvents(orgId, incidentId, userDetails.getUser().getId()));
  }
}
