package com.servicedna.service.controller;

import com.servicedna.service.dto.PublicStatusPageDto;
import com.servicedna.service.service.PublicStatusService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/organizations/{orgId}/status")
public class PublicStatusController {

  private final PublicStatusService publicStatusService;

  public PublicStatusController(PublicStatusService publicStatusService) {
    this.publicStatusService = publicStatusService;
  }

  @GetMapping
  public ResponseEntity<PublicStatusPageDto> getStatusPage(@PathVariable UUID orgId) {
    return ResponseEntity.ok(publicStatusService.getPublicStatusPage(orgId));
  }
}
