package com.servicedna.organization.controller;

import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.organization.dto.CreateInviteRequest;
import com.servicedna.organization.dto.InviteDto;
import com.servicedna.organization.service.OrganizationInviteService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrganizationInviteController {

  private final OrganizationInviteService inviteService;

  public OrganizationInviteController(OrganizationInviteService inviteService) {
    this.inviteService = inviteService;
  }

  @PostMapping("/organizations/{orgId}/invites")
  public ResponseEntity<InviteDto> createInvite(
      @PathVariable UUID orgId,
      @Valid @RequestBody CreateInviteRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return new ResponseEntity<>(
        inviteService.createInvite(orgId, request, userDetails.getUser().getId()),
        HttpStatus.CREATED);
  }

  @GetMapping("/organizations/{orgId}/invites")
  public ResponseEntity<List<InviteDto>> getInvites(
      @PathVariable UUID orgId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(inviteService.getInvites(orgId, userDetails.getUser().getId()));
  }

  @PostMapping("/invites/{token}/accept")
  public ResponseEntity<Void> acceptInvite(
      @PathVariable String token, @AuthenticationPrincipal CustomUserDetails userDetails) {
    inviteService.acceptInvite(token, userDetails.getUser().getId());
    return ResponseEntity.ok().build();
  }
}
