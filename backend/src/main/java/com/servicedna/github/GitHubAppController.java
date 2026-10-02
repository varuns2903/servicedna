package com.servicedna.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.security.CustomUserDetails;
import java.io.IOException;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GitHubAppController {

  private final GitHubAppService service;
  private final ObjectMapper json;

  public GitHubAppController(GitHubAppService service, ObjectMapper json) {
    this.service = service;
    this.json = json;
  }

  /** GitHub's webhook deliveries: verified by signature, answered at once, processed in the background. */
  @PostMapping("/api/v1/github/webhook")
  public ResponseEntity<Void> webhook(
      @RequestHeader(value = "X-GitHub-Event", required = false) String event,
      @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
      @RequestBody byte[] payload) throws IOException {
    if (!service.verifySignature(payload, signature)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (event != null && !"ping".equals(event)) {
      service.handle(event, json.readTree(payload));
    }
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/api/v1/organizations/{orgId}/github/app")
  public ResponseEntity<GitHubAppService.Status> status(@PathVariable UUID orgId, @AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(service.status(orgId, user.getUser().getId()));
  }

  /** Called by the setup page GitHub redirects to after installing the app. */
  @PostMapping("/api/v1/organizations/{orgId}/github/installations")
  public ResponseEntity<GitHubAppService.Installation> connect(
      @PathVariable UUID orgId, @RequestBody GitHubAppService.ConnectRequest request, @AuthenticationPrincipal CustomUserDetails user) {
    return new ResponseEntity<>(service.connect(orgId, user.getUser().getId(), request), HttpStatus.CREATED);
  }

  @DeleteMapping("/api/v1/organizations/{orgId}/github/installations/{installationId}")
  public ResponseEntity<Void> disconnect(
      @PathVariable UUID orgId, @PathVariable long installationId, @AuthenticationPrincipal CustomUserDetails user) {
    service.disconnect(orgId, user.getUser().getId(), installationId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/v1/organizations/{orgId}/github/installations/{installationId}/sync")
  public ResponseEntity<List<GitHubAppService.SyncResult>> sync(
      @PathVariable UUID orgId, @PathVariable long installationId, @AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(service.syncNow(orgId, user.getUser().getId(), installationId));
  }
}
