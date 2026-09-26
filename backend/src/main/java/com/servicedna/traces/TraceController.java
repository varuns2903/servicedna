package com.servicedna.traces;

import com.servicedna.auth.security.CustomUserDetails;
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
@RequestMapping("/api/v1/organizations/{orgId}/traces")
public class TraceController {

  private final TraceQueryService traceQueryService;

  public TraceController(TraceQueryService traceQueryService) {
    this.traceQueryService = traceQueryService;
  }

  /**
   * Recent traces through an operation ({@code service} + optional {@code operation}), or — with
   * {@code callee} too — where that operation called the callee's.
   */
  @GetMapping
  public ResponseEntity<List<TraceDto.Summary>> search(
      @PathVariable UUID orgId,
      @RequestParam(required = false) String service,
      @RequestParam(required = false) String operation,
      @RequestParam(required = false) String calleeService,
      @RequestParam(required = false) String calleeOperation,
      @RequestParam(defaultValue = "false") boolean errorsOnly,
      @RequestParam(defaultValue = "60") int windowMinutes,
      @RequestParam(defaultValue = "20") int limit,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    TraceQueryService.Hop caller = service != null ? new TraceQueryService.Hop(service, operation) : null;
    TraceQueryService.Hop callee = calleeService != null ? new TraceQueryService.Hop(calleeService, calleeOperation) : null;
    return ResponseEntity.ok(
        traceQueryService.search(orgId, caller, callee, errorsOnly, windowMinutes, limit, userDetails.getUser().getId()));
  }

  @GetMapping("/{traceId}")
  public ResponseEntity<TraceDto.Trace> trace(
      @PathVariable UUID orgId, @PathVariable String traceId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(traceQueryService.trace(orgId, traceId, userDetails.getUser().getId()));
  }
}
