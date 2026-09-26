package com.servicedna.graph.controller;

import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.graph.dto.FlowDto;
import com.servicedna.graph.service.FlowService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/flows")
public class FlowController {

  private final FlowService flowService;

  public FlowController(FlowService flowService) {
    this.flowService = flowService;
  }

  /** Operation-level calls; with entryNode + entryOperation, just what that operation triggers. */
  @GetMapping
  public ResponseEntity<FlowDto> flows(
      @PathVariable UUID orgId,
      @RequestParam(defaultValue = "60") int windowMinutes,
      @RequestParam(required = false) String entryNode,
      @RequestParam(required = false) String entryOperation,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        flowService.flows(orgId, windowMinutes, entryNode, entryOperation, userDetails.getUser().getId()));
  }

  @GetMapping("/entry-points")
  public ResponseEntity<List<FlowDto.EntryPoint>> entryPoints(
      @PathVariable UUID orgId,
      @RequestParam(defaultValue = "60") int windowMinutes,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(flowService.entryPoints(orgId, windowMinutes, userDetails.getUser().getId()));
  }
}
