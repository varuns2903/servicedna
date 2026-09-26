package com.servicedna.testrun;

import com.servicedna.auth.security.CustomUserDetails;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/environments")
public class EnvironmentSettingsController {

  private final EnvironmentSettingsService service;

  public EnvironmentSettingsController(EnvironmentSettingsService service) {
    this.service = service;
  }

  public record UpdateRequest(boolean allowTestRuns) {}

  @GetMapping
  public ResponseEntity<List<EnvironmentSettingsService.Setting>> list(
      @PathVariable UUID orgId, @AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(service.list(orgId, user.getUser().getId()));
  }

  @PutMapping("/{environment}")
  public ResponseEntity<EnvironmentSettingsService.Setting> update(
      @PathVariable UUID orgId, @PathVariable String environment, @RequestBody UpdateRequest request,
      @AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(service.update(orgId, environment, request.allowTestRuns(), user.getUser().getId()));
  }
}
