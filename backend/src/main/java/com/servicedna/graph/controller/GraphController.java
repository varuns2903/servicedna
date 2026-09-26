package com.servicedna.graph.controller;

import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.graph.dto.GraphDto;
import com.servicedna.graph.service.GraphService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/graph")
public class GraphController {

  private final GraphService graphService;

  public GraphController(GraphService graphService) {
    this.graphService = graphService;
  }

  /** Declared and observed dependencies over the last {@code windowMinutes} (max 7 days). */
  @GetMapping
  public ResponseEntity<GraphDto> graph(
      @PathVariable UUID orgId,
      @RequestParam(defaultValue = "60") int windowMinutes,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(graphService.graph(orgId, windowMinutes, userDetails.getUser().getId()));
  }
}
