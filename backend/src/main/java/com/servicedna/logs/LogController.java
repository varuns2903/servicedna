package com.servicedna.logs;

import com.servicedna.auth.security.CustomUserDetails;
import java.time.Instant;
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
@RequestMapping("/api/v1/organizations/{orgId}/logs")
public class LogController {

  private final LogQueryService logQueryService;

  public LogController(LogQueryService logQueryService) {
    this.logQueryService = logQueryService;
  }

  /**
   * Log search: every filter optional; {@code attribute} repeats ({@code orderId=o-17});
   * {@code traceId} gives a trace's logs; {@code q} is raw LogQL and overrides the filters.
   */
  @GetMapping
  public ResponseEntity<LogDto.Search> search(
      @PathVariable UUID orgId,
      @RequestParam(required = false) String service,
      @RequestParam(required = false) String environment,
      @RequestParam(required = false) String level,
      @RequestParam(required = false) String text,
      @RequestParam(required = false) String traceId,
      @RequestParam(required = false) List<String> attribute,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "200") int limit,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    LogQl.Filters filters = new LogQl.Filters(service, environment, level, text, traceId, attribute);
    return ResponseEntity.ok(logQueryService.search(orgId, filters, q, from, to, limit, userDetails.getUser().getId()));
  }
}
