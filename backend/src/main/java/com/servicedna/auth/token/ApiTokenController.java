package com.servicedna.auth.token;

import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.auth.security.JwtAuthenticationFilter;
import com.servicedna.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/users/me/tokens")
public class ApiTokenController {

  private final ApiTokenService service;

  public ApiTokenController(ApiTokenService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<List<ApiTokenService.Token>> list(@AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(service.list(user.getUser().getId()));
  }

  @PostMapping
  public ResponseEntity<ApiTokenService.Token> create(
      @RequestBody ApiTokenService.CreateRequest request, @AuthenticationPrincipal CustomUserDetails user, HttpServletRequest http) {
    requireSession(http);
    return new ResponseEntity<>(service.create(user.getUser().getId(), request), HttpStatus.CREATED);
  }

  @DeleteMapping("/{tokenId}")
  public ResponseEntity<Void> revoke(@PathVariable UUID tokenId, @AuthenticationPrincipal CustomUserDetails user, HttpServletRequest http) {
    requireSession(http);
    service.revoke(user.getUser().getId(), tokenId);
    return ResponseEntity.noContent().build();
  }

  /** Tokens are managed from a signed-in session, so a leaked token can't mint more. */
  private static void requireSession(HttpServletRequest http) {
    if (http.getAttribute(JwtAuthenticationFilter.API_TOKEN_ATTRIBUTE) != null) {
      throw new ApiException(HttpStatus.FORBIDDEN, "SESSION_REQUIRED", "API tokens can't create or revoke tokens; sign in to manage them.");
    }
  }
}
