package com.servicedna.organization.controller;

import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.organization.dto.AddMemberRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.organization.dto.OrganizationDto;
import com.servicedna.organization.dto.OrganizationMemberDto;
import com.servicedna.organization.service.OrganizationService;
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
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

  private final OrganizationService organizationService;

  public OrganizationController(OrganizationService organizationService) {
    this.organizationService = organizationService;
  }

  @PostMapping
  public ResponseEntity<OrganizationDto> createOrganization(
      @Valid @RequestBody CreateOrganizationRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return new ResponseEntity<>(
        organizationService.createOrganization(request, userDetails.getUser().getId()),
        HttpStatus.CREATED);
  }

  @GetMapping
  public ResponseEntity<List<OrganizationDto>> getUserOrganizations(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        organizationService.getUserOrganizations(userDetails.getUser().getId()));
  }

  @PostMapping("/{orgId}/members")
  public ResponseEntity<OrganizationMemberDto> addMember(
      @PathVariable UUID orgId,
      @Valid @RequestBody AddMemberRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return new ResponseEntity<>(
        organizationService.addMember(orgId, request, userDetails.getUser().getId()),
        HttpStatus.CREATED);
  }
}
