package com.servicedna.github;

import com.servicedna.auth.security.CustomUserDetails;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/github/import")
public class GitHubImportController {

  private final GitHubImportService service;

  public GitHubImportController(GitHubImportService service) {
    this.service = service;
  }

  /** The GitHub organization's repositories, with what each one's servicedna.yaml says. */
  @PostMapping("/preview")
  public ResponseEntity<List<GitHubImportService.Repository>> preview(
      @PathVariable UUID orgId, @RequestBody GitHubImportService.Source source, @AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(service.preview(orgId, source, user.getUser().getId()));
  }

  @PostMapping
  public ResponseEntity<List<GitHubImportService.Imported>> importRepositories(
      @PathVariable UUID orgId, @RequestBody GitHubImportService.ImportRequest request, @AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(service.importRepositories(orgId, request, user.getUser().getId()));
  }
}
