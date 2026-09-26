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

  /**
   * Explorer search: every filter optional; {@code attribute} repeats ({@code orderId=o-17},
   * {@code http.response.status_code>=500}); {@code q} is raw TraceQL and overrides the filters.
   * {@code from}/{@code to} are ISO instants (default: the last hour).
   */
  @GetMapping("/explore")
  public ResponseEntity<TraceDto.Explore> explore(
      @PathVariable UUID orgId,
      @RequestParam(required = false) String service,
      @RequestParam(required = false) String operation,
      @RequestParam(required = false) String environment,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long minDurationMs,
      @RequestParam(required = false) Long maxDurationMs,
      @RequestParam(required = false) List<String> attribute,
      @RequestParam(required = false) String text,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) java.time.Instant from,
      @RequestParam(required = false) java.time.Instant to,
      @RequestParam(defaultValue = "50") int limit,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    TraceQl.Filters filters = new TraceQl.Filters(service, operation, environment, status, minDurationMs, maxDurationMs, attribute, text);
    return ResponseEntity.ok(traceQueryService.explore(orgId, filters, q, from, to, limit, userDetails.getUser().getId()));
  }

  @GetMapping("/attributes")
  public ResponseEntity<List<String>> attributes(@PathVariable UUID orgId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(traceQueryService.attributeNames(orgId, userDetails.getUser().getId()));
  }

  @GetMapping("/attributes/values")
  public ResponseEntity<List<String>> attributeValues(
      @PathVariable UUID orgId, @RequestParam String name, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(traceQueryService.attributeValues(orgId, name, userDetails.getUser().getId()));
  }

  @GetMapping("/{traceId}")
  public ResponseEntity<TraceDto.Trace> trace(
      @PathVariable UUID orgId, @PathVariable String traceId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(traceQueryService.trace(orgId, traceId, userDetails.getUser().getId()));
  }
}
