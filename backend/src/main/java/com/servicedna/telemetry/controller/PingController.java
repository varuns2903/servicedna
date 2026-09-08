package com.servicedna.telemetry.controller;

import com.servicedna.telemetry.dto.PingRequest;
import com.servicedna.telemetry.dto.PingResponse;
import com.servicedna.telemetry.service.PingService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ping")
public class PingController {

  private final PingService pingService;

  public PingController(PingService pingService) {
    this.pingService = pingService;
  }

  @PostMapping
  public ResponseEntity<PingResponse> processPing(
      @RequestHeader("X-API-Key") String apiKey, @Valid @RequestBody PingRequest request) {
    pingService.processPing(apiKey, request);
    return ResponseEntity.ok(new PingResponse(true, OffsetDateTime.now()));
  }
}
