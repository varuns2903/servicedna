package com.servicedna.catalog.controller;

import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.catalog.dto.CatalogDto;
import com.servicedna.catalog.service.CatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {

  private final CatalogService catalogService;

  public CatalogController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  /** Results of `sdna scan`: a service's spec-declared operations and configured dependencies. */
  @PostMapping("/api/v1/organizations/{orgId}/catalog/scan")
  public ResponseEntity<CatalogDto.ScanResult> scan(
      @PathVariable UUID orgId,
      @Valid @RequestBody CatalogDto.ScanRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(catalogService.applyScan(orgId, request, userDetails.getUser().getId()));
  }

  @GetMapping("/api/v1/organizations/{orgId}/services/{serviceId}/operations")
  public ResponseEntity<List<CatalogDto.Operation>> operations(
      @PathVariable UUID orgId,
      @PathVariable UUID serviceId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(catalogService.operations(orgId, serviceId, userDetails.getUser().getId()));
  }
}
