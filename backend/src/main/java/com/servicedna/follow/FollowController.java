package com.servicedna.follow;

import com.servicedna.auth.security.CustomUserDetails;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/follow")
public class FollowController {

  private final FollowService followService;

  public FollowController(FollowService followService) {
    this.followService = followService;
  }

  /** Everything that touched a business key ({@code key=orderId&value=o-17}); default: the last day. */
  @GetMapping
  public ResponseEntity<FollowService.Story> follow(
      @PathVariable UUID orgId,
      @RequestParam String key,
      @RequestParam String value,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(followService.follow(orgId, key, value, from, to, userDetails.getUser().getId()));
  }
}
