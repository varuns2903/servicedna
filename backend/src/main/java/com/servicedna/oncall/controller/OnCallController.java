package com.servicedna.oncall.controller;

import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.oncall.dto.OnCallRotationDto;
import com.servicedna.oncall.dto.UpsertOnCallRotationRequest;
import com.servicedna.oncall.service.OnCallService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/organizations/{orgId}/on-call")
public class OnCallController {

  private final OnCallService onCallService;

  public OnCallController(OnCallService onCallService) {
    this.onCallService = onCallService;
  }

  @GetMapping
  public ResponseEntity<OnCallRotationDto> getRotation(
      @PathVariable UUID orgId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(onCallService.getRotation(orgId, userDetails.getUser().getId()));
  }

  @PutMapping
  public ResponseEntity<OnCallRotationDto> upsertRotation(
      @PathVariable UUID orgId,
      @Valid @RequestBody UpsertOnCallRotationRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        onCallService.upsertRotation(orgId, request, userDetails.getUser().getId()));
  }
}
