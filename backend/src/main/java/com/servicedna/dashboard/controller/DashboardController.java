package com.servicedna.dashboard.controller;

import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.dashboard.dto.DashboardSummaryDto;
import com.servicedna.dashboard.service.DashboardService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/dashboard")
public class DashboardController {

  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  @GetMapping
  public ResponseEntity<DashboardSummaryDto> getDashboardSummary(
      @PathVariable UUID orgId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        dashboardService.getDashboardSummary(orgId, userDetails.getUser().getId()));
  }
}
