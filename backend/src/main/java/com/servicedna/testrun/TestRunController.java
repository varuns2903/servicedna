package com.servicedna.testrun;

import com.servicedna.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/test-runs")
public class TestRunController {

  private final TestRunService testRunService;
  private final TestRunViews views;
  private final TestRunReplay replay;

  public TestRunController(TestRunService testRunService, TestRunViews views, TestRunReplay replay) {
    this.testRunService = testRunService;
    this.views = views;
    this.replay = replay;
  }

  /** Re-sends the request a trace recorded ({@code spanId}, or its entry request) as a test run. */
  @PostMapping("/replay")
  public ResponseEntity<TestRunDto.Run> replay(
      @PathVariable UUID orgId, @RequestBody TestRunReplay.ReplayRequest request, @AuthenticationPrincipal CustomUserDetails user) {
    return new ResponseEntity<>(views.summary(replay.replay(orgId, request, user.getUser().getId())), HttpStatus.CREATED);
  }

  @PostMapping
  public ResponseEntity<TestRunDto.Run> create(
      @PathVariable UUID orgId, @Valid @RequestBody TestRunDto.CreateRequest request, @AuthenticationPrincipal CustomUserDetails user) {
    return new ResponseEntity<>(views.summary(testRunService.create(orgId, request, user.getUser().getId())), HttpStatus.CREATED);
  }

  @GetMapping
  public ResponseEntity<List<TestRunDto.Run>> list(
      @PathVariable UUID orgId, @RequestParam(defaultValue = "50") int limit, @AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(testRunService.list(orgId, limit, user.getUser().getId()).stream().map(views::summary).toList());
  }

  /** The run, with its hops once the trace has arrived. */
  @GetMapping("/{runId}")
  public ResponseEntity<TestRunDto.Run> get(
      @PathVariable UUID orgId, @PathVariable UUID runId, @AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(views.detail(testRunService.get(orgId, runId, user.getUser().getId())));
  }
}
