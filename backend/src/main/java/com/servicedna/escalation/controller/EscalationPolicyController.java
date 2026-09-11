package com.servicedna.escalation.controller;

import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.escalation.dto.EscalationPolicyDto;
import com.servicedna.escalation.dto.UpsertEscalationPolicyRequest;
import com.servicedna.escalation.service.EscalationPolicyService;
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
@RequestMapping("/api/v1/organizations/{orgId}/escalation-policy")
public class EscalationPolicyController {

  private final EscalationPolicyService escalationPolicyService;

  public EscalationPolicyController(EscalationPolicyService escalationPolicyService) {
    this.escalationPolicyService = escalationPolicyService;
  }

  @GetMapping
  public ResponseEntity<EscalationPolicyDto> getPolicy(
      @PathVariable UUID orgId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(escalationPolicyService.getPolicy(orgId, userDetails.getUser().getId()));
  }

  @PutMapping
  public ResponseEntity<EscalationPolicyDto> upsertPolicy(
      @PathVariable UUID orgId,
      @Valid @RequestBody UpsertEscalationPolicyRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        escalationPolicyService.upsertPolicy(orgId, request, userDetails.getUser().getId()));
  }
}
