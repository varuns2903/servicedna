package com.servicedna.testrun;

import com.servicedna.common.exception.ApiException;
import com.servicedna.ingestion.service.IngestionKeyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The runner's side of test runs. A runner authenticates with an organization ingestion key (and
 * only sees that organization's runs), or — for the runner bundled with a self-hosted ServiceDNA —
 * with RUNNER_SHARED_TOKEN, serving every organization.
 */
@RestController
@RequestMapping("/api/v1/runner")
public class RunnerController {

  private static final long POLL_INTERVAL_MS = 500;

  private final TestRunService testRunService;
  private final TestRunViews views;
  private final IngestionKeyService ingestionKeyService;
  private final String sharedToken;
  private final long longPollMs;

  public RunnerController(
      TestRunService testRunService,
      TestRunViews views,
      IngestionKeyService ingestionKeyService,
      @Value("${runner.shared-token:}") String sharedToken,
      @Value("${runner.long-poll-ms:20000}") long longPollMs) {
    this.testRunService = testRunService;
    this.views = views;
    this.ingestionKeyService = ingestionKeyService;
    this.sharedToken = sharedToken;
    this.longPollMs = longPollMs;
  }

  public record ClaimRequest(@Size(max = 64) String environment, @Size(max = 100) String runner) {}

  /** Long-polls up to 20 s for a run this runner may send; 204 when there's none. */
  @PostMapping("/claim")
  public ResponseEntity<TestRunDto.Job> claim(
      @RequestHeader(value = "x-servicedna-key", required = false) String key,
      @RequestHeader(value = "X-Runner-Token", required = false) String token,
      @Valid @RequestBody ClaimRequest request) throws InterruptedException {
    Scope scope = authenticate(key, token);
    String runner = request.runner() == null || request.runner().isBlank() ? "runner" : request.runner();
    long deadline = System.currentTimeMillis() + longPollMs;
    do {
      Optional<TestRun> run = testRunService.claim(scope.organizationId(), request.environment(), runner);
      if (run.isPresent()) {
        TestRun r = run.get();
        return ResponseEntity.ok(new TestRunDto.Job(r.getId(), r.getOrganizationId(), r.getProtocol(),
            testRunService.readJson(r.getTarget()), testRunService.readJson(r.getRequest()), r.getTraceId()));
      }
      Thread.sleep(Math.min(POLL_INTERVAL_MS, longPollMs));
    } while (System.currentTimeMillis() < deadline);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/runs/{runId}/result")
  public ResponseEntity<Void> result(
      @RequestHeader(value = "x-servicedna-key", required = false) String key,
      @RequestHeader(value = "X-Runner-Token", required = false) String token,
      @PathVariable UUID runId,
      @RequestBody TestRunDto.JobResult result) {
    testRunService.report(authenticate(key, token).organizationId(), runId, result);
    return ResponseEntity.noContent().build();
  }

  /** {@code organizationId} null means the shared runner (all organizations). */
  private record Scope(UUID organizationId) {}

  private Scope authenticate(String key, String token) {
    if (token != null && !sharedToken.isBlank()
        && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), sharedToken.getBytes(StandardCharsets.UTF_8))) {
      return new Scope(null);
    }
    return ingestionKeyService.authenticate(key).map(Scope::new)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_RUNNER_CREDENTIALS", "Runners authenticate with an ingestion key."));
  }
}
