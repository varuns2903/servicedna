package com.servicedna.service.controller;

import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.service.domain.MaintenanceStatus;
import com.servicedna.service.dto.CreateMaintenanceWindowRequest;
import com.servicedna.service.dto.MaintenanceWindowDto;
import com.servicedna.service.service.MaintenanceWindowService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/maintenance")
public class MaintenanceWindowController {

  private final MaintenanceWindowService maintenanceWindowService;

  public MaintenanceWindowController(MaintenanceWindowService maintenanceWindowService) {
    this.maintenanceWindowService = maintenanceWindowService;
  }

  @PostMapping
  public ResponseEntity<MaintenanceWindowDto> createMaintenanceWindow(
      @PathVariable UUID orgId,
      @Valid @RequestBody CreateMaintenanceWindowRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return new ResponseEntity<>(
        maintenanceWindowService.createMaintenanceWindow(
            orgId, request, userDetails.getUser().getId()),
        HttpStatus.CREATED);
  }

  @GetMapping
  public ResponseEntity<List<MaintenanceWindowDto>> getMaintenanceWindows(
      @PathVariable UUID orgId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        maintenanceWindowService.getMaintenanceWindows(orgId, userDetails.getUser().getId()));
  }

  @PutMapping("/{windowId}/status")
  public ResponseEntity<MaintenanceWindowDto> updateMaintenanceStatus(
      @PathVariable UUID orgId,
      @PathVariable UUID windowId,
      @RequestParam MaintenanceStatus status,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        maintenanceWindowService.updateMaintenanceStatus(
            orgId, windowId, status, userDetails.getUser().getId()));
  }
}
