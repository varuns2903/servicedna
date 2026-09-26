package com.servicedna.ingestion.controller;

import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.ingestion.dto.CreateIngestionKeyRequest;
import com.servicedna.ingestion.dto.IngestionKeyDto;
import com.servicedna.ingestion.service.IngestionKeyService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/organizations/{orgId}/ingestion-keys")
public class IngestionKeyController {

  private final IngestionKeyService ingestionKeyService;

  public IngestionKeyController(IngestionKeyService ingestionKeyService) {
    this.ingestionKeyService = ingestionKeyService;
  }

  @GetMapping
  public ResponseEntity<List<IngestionKeyDto>> list(
      @PathVariable UUID orgId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(ingestionKeyService.list(orgId, userDetails.getUser().getId()));
  }

  @PostMapping
  public ResponseEntity<IngestionKeyDto> create(
      @PathVariable UUID orgId,
      @Valid @RequestBody CreateIngestionKeyRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return new ResponseEntity<>(
        ingestionKeyService.create(orgId, request.name(), userDetails.getUser().getId()),
        HttpStatus.CREATED);
  }

  @DeleteMapping("/{keyId}")
  public ResponseEntity<IngestionKeyDto> revoke(
      @PathVariable UUID orgId,
      @PathVariable UUID keyId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(ingestionKeyService.revoke(orgId, keyId, userDetails.getUser().getId()));
  }
}
