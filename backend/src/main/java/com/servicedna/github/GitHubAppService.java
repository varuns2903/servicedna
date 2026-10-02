package com.servicedna.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.testrun.FlowFiles;
import com.servicedna.testrun.SuiteFinishedEvent;
import com.servicedna.testrun.TestRunDto;
import com.servicedna.testrun.TestSuiteService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The ServiceDNA GitHub App: connects a GitHub organization's installation to a ServiceDNA
 * organization, keeps services in step with each repository's servicedna.yaml (on install, and
 * on pushes to the default branch that change it), and reports a "ServiceDNA" check on pull
 * requests — servicedna.yaml validated, and flows/*.yaml run as suites. Syncs and checks act as
 * the ServiceDNA user who connected the installation.
 *
 * <p>GitHub tells it about changes by webhook — or, for a ServiceDNA GitHub can't reach
 * ({@code github.app.poll}), it asks GitHub every minute instead, so nothing has to be exposed
 * to the internet.
 */
@Service
public class GitHubAppService {

  private static final Logger log = LoggerFactory.getLogger(GitHubAppService.class);
  static final String CHECK_NAME = "ServiceDNA";
  private static final long STATE_TTL_SECONDS = 3600;
  private static final Set<String> PR_ACTIONS = Set.of("opened", "synchronize", "reopened", "ready_for_review");

  private final GitHubAppClient github;
  private final GitHubInstallationRepository installations;
  private final GitHubCheckRunRepository checkRuns;
  private final GitHubImportService imports;
  private final TestSuiteService suites;
  private final OrganizationService organizationService;
  private final TransactionTemplate transactions;
  private final GitHubPollHeads heads;
  private final boolean polling;
  private final String frontendUrl;
  // Webhooks must be answered within seconds; the work happens here, in order.
  private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "github-app");
    t.setDaemon(true);
    return t;
  });

  public GitHubAppService(GitHubAppClient github, GitHubInstallationRepository installations, GitHubCheckRunRepository checkRuns,
      GitHubImportService imports, TestSuiteService suites, OrganizationService organizationService, TransactionTemplate transactions,
      GitHubPollHeads heads, @Value("${github.app.poll:false}") boolean polling,
      @Value("${frontend.url:http://localhost:5173}") String frontendUrl) {
    this.github = github;
    this.installations = installations;
    this.checkRuns = checkRuns;
    this.imports = imports;
    this.suites = suites;
    this.organizationService = organizationService;
    this.transactions = transactions;
    this.heads = heads;
    this.polling = polling;
    this.frontendUrl = frontendUrl.replaceAll("/+$", "");
  }

  public record Installation(long installationId, String account, OffsetDateTime installedAt, OffsetDateTime lastSyncedAt) {}

  public record Status(boolean configured, String slug, String installUrl, List<Installation> installations) {}

  public record ConnectRequest(Long installationId, String code, String state) {}

  public record SyncResult(String repository, String service, boolean ok, String message) {}

  // --- connecting ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public Status status(UUID organizationId, UUID userId) {
    OrganizationMember member = organizationService.validateUserAccess(organizationId, userId);
    List<Installation> linked = installations.findByOrganizationIdOrderByInstalledAt(organizationId).stream().map(GitHubAppService::view).toList();
    if (!github.isConfigured()) {
      return new Status(false, null, null, linked);
    }
    String installUrl = isAdmin(member) ? github.installUrl(state(organizationId, userId, Instant.now().getEpochSecond() + STATE_TTL_SECONDS)) : null;
    return new Status(true, github.slug(), installUrl, linked);
  }

  /**
   * Links an installation after GitHub redirects back from installing the app. The signed state
   * says which organization and user started it; the user's GitHub authorization proves they can
   * administer the installation — so nobody can attach another company's installation.
   */
  public Installation connect(UUID organizationId, UUID userId, ConnectRequest request) {
    requireAdmin(organizationId, userId);
    if (request.installationId() == null || request.code() == null || request.state() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GITHUB_SETUP", "GitHub didn't send the installation, code and state back.");
    }
    verifyState(request.state(), organizationId, userId);
    String userToken = github.userToken(request.code());
    if (!github.userInstallations(userToken).contains(request.installationId())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "GITHUB_INSTALLATION_NOT_YOURS", "Your GitHub account can't administer that installation.");
    }
    String account = github.installation(request.installationId()).path("account").path("login").asText();
    GitHubInstallation saved = transactions.execute(status -> {
      Optional<GitHubInstallation> existing = installations.findById(request.installationId());
      if (existing.isPresent() && !existing.get().getOrganizationId().equals(organizationId)) {
        throw new ApiException(HttpStatus.CONFLICT, "GITHUB_INSTALLATION_TAKEN", "That GitHub installation is connected to another organization.");
      }
      return existing.orElseGet(() -> installations.save(new GitHubInstallation(request.installationId(), organizationId, account, userId)));
    });
    worker.submit(() -> syncAll(saved.getInstallationId()));
    if (polling) {
      worker.submit(() -> poll(saved));
    }
    return view(saved);
  }

  @Transactional
  public void disconnect(UUID organizationId, UUID userId, long installationId) {
    requireAdmin(organizationId, userId);
    installations.findById(installationId).filter(i -> i.getOrganizationId().equals(organizationId)).ifPresent(installations::delete);
  }

  /** Syncs every repository with a servicedna.yaml now (also done on connect). */
  public List<SyncResult> syncNow(UUID organizationId, UUID userId, long installationId) {
    requireAdmin(organizationId, userId);
    installations.findById(installationId).filter(i -> i.getOrganizationId().equals(organizationId))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GITHUB_INSTALLATION_NOT_FOUND", "Installation not found"));
    return syncAll(installationId);
  }

  // --- syncing ------------------------------------------------------------------------------

  List<SyncResult> syncAll(long installationId) {
    List<SyncResult> results = new ArrayList<>();
    Optional<GitHubInstallation> installation = installations.findById(installationId);
    if (installation.isEmpty()) {
      return results;
    }
    for (JsonNode repo : github.repositories(installationId)) {
      if (!repo.path("archived").asBoolean()) {
        syncRepository(installation.get(), repo, true).ifPresent(results::add);
      }
    }
    transactions.executeWithoutResult(status -> installations.findById(installationId).ifPresent(i -> {
      i.setLastSyncedAt(OffsetDateTime.now());
      installations.save(i);
    }));
    return results;
  }

  /**
   * Applies the repository's servicedna.yaml on its default branch; repositories without one are
   * left out. Unless forced, an unchanged servicedna.yaml isn't applied again.
   */
  Optional<SyncResult> syncRepository(GitHubInstallation installation, JsonNode repo, boolean force) {
    String fullName = repo.path("full_name").asText();
    long installationId = installation.getInstallationId();
    try {
      Optional<String> manifest = manifest(installationId, fullName, repo.path("default_branch").asText(null));
      if (manifest.isEmpty()) {
        heads.forget(installationId, fullName, "manifest");
        return Optional.empty();
      }
      if (!heads.advance(installationId, fullName, "manifest", sha256(manifest.get())) && !force) {
        return Optional.empty();
      }
      var imported = imports.apply(installation.getOrganizationId(), repo, manifest.get(), installation.getInstalledBy());
      return Optional.of(new SyncResult(fullName, imported.service(), true, imported.message()));
    } catch (ApiException e) {
      log.info("GitHub sync of {} failed: {}", fullName, e.getMessage());
      heads.forget(installationId, fullName, "manifest"); // try again next time
      return Optional.of(new SyncResult(fullName, null, false, e.getMessage()));
    }
  }

  private Optional<String> manifest(long installationId, String repository, String ref) {
    Optional<String> yaml = github.file(installationId, repository, "servicedna.yaml", ref);
    return yaml.isPresent() ? yaml : github.file(installationId, repository, "servicedna.yml", ref);
  }

  // --- webhooks -----------------------------------------------------------------------------

  /** True when the payload was signed with the app's webhook secret. */
  public boolean verifySignature(byte[] payload, String signatureHeader) {
    if (!github.isConfigured() || signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
      return false;
    }
    byte[] expected = hmac(github.webhookSecret(), payload);
    byte[] given;
    try {
      given = HexFormat.of().parseHex(signatureHeader.substring("sha256=".length()));
    } catch (IllegalArgumentException e) {
      return false;
    }
    return MessageDigest.isEqual(expected, given);
  }

  /** Queues a verified webhook delivery. */
  public void handle(String event, JsonNode payload) {
    worker.submit(() -> {
      try {
        process(event, payload);
      } catch (RuntimeException e) {
        log.warn("GitHub {} webhook failed: {}", event, e.getMessage());
      }
    });
  }

  void process(String event, JsonNode payload) {
    long installationId = payload.path("installation").path("id").asLong();
    Optional<GitHubInstallation> installation = installations.findById(installationId);
    switch (event) {
      case "installation" -> {
        String action = payload.path("action").asText();
        if (("deleted".equals(action) || "suspend".equals(action)) && installation.isPresent()) {
          transactions.executeWithoutResult(status -> installations.deleteById(installationId));
        }
      }
      case "installation_repositories" -> installation.ifPresent(i -> payload.path("repositories_added").forEach(added ->
          syncRepository(i, github.get(installationId, "/repos/" + added.path("full_name").asText()), true)));
      case "push" -> installation.ifPresent(i -> {
        JsonNode repo = payload.path("repository");
        if (!("refs/heads/" + repo.path("default_branch").asText()).equals(payload.path("ref").asText())) {
          return;
        }
        boolean touched = false;
        for (JsonNode commit : payload.path("commits")) {
          for (String field : List.of("added", "modified")) {
            for (JsonNode file : commit.path(field)) {
              touched |= file.asText().equals("servicedna.yaml") || file.asText().equals("servicedna.yml");
            }
          }
        }
        if (touched) {
          syncRepository(i, repo, true);
        }
      });
      case "pull_request" -> installation.ifPresent(i -> {
        if (PR_ACTIONS.contains(payload.path("action").asText()) && !payload.path("pull_request").path("draft").asBoolean()) {
          check(i, payload.path("repository"), payload.path("pull_request").path("head").path("sha").asText());
        }
      });
      default -> { }
    }
  }

  // --- polling ------------------------------------------------------------------------------

  /** With {@code github.app.poll}: asks GitHub what changed, in place of webhooks. */
  @Scheduled(initialDelayString = "${github.app.poll-interval-ms:60000}", fixedDelayString = "${github.app.poll-interval-ms:60000}")
  public void pollAll() {
    if (!polling || !github.isConfigured()) {
      return;
    }
    try {
      worker.submit(() -> installations.findAll().forEach(installation -> {
        try {
          poll(installation);
        } catch (RuntimeException e) {
          log.warn("Polling GitHub installation {} failed: {}", installation.getInstallationId(), e.getMessage());
        }
      })).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      log.warn("Polling GitHub failed: {}", e.getCause().getMessage());
    }
  }

  /**
   * What webhooks would have said: an uninstalled app, a push that changed servicedna.yaml, a new
   * repository, and pull requests opened or pushed to (each head is checked once).
   */
  void poll(GitHubInstallation installation) {
    long installationId = installation.getInstallationId();
    try {
      if (github.installation(installationId).hasNonNull("suspended_at")) {
        return;
      }
    } catch (ApiException e) {
      if (e.getStatus() == HttpStatus.NOT_FOUND) { // uninstalled
        transactions.executeWithoutResult(status -> installations.deleteById(installationId));
        return;
      }
      throw e;
    }
    Set<String> covered = new HashSet<>();
    for (JsonNode repo : github.repositories(installationId)) {
      String fullName = repo.path("full_name").asText();
      if (repo.path("archived").asBoolean()) {
        continue;
      }
      covered.add(fullName);
      try {
        if (heads.advance(installationId, fullName, "pushed", repo.path("pushed_at").asText(""))
            && syncRepository(installation, repo, false).filter(r -> !r.ok()).isPresent()) {
          heads.forget(installationId, fullName, "pushed"); // try again next time
        }
        Set<String> open = new HashSet<>();
        for (JsonNode pull : github.get(installationId, "/repos/" + fullName + "/pulls?state=open&per_page=100")) {
          if (pull.path("draft").asBoolean()) {
            continue; // checked once it's ready for review
          }
          String ref = "pr:" + pull.path("number").asLong();
          open.add(ref);
          String sha = pull.path("head").path("sha").asText();
          if (heads.advance(installationId, fullName, ref, sha)) {
            try {
              check(installation, repo, sha);
            } catch (RuntimeException e) {
              heads.forget(installationId, fullName, ref); // try again next time
              throw e;
            }
          }
        }
        heads.keepPullRequests(installationId, fullName, open);
      } catch (RuntimeException e) {
        heads.forget(installationId, fullName, "pushed");
        log.info("Polling {} failed: {}", fullName, e.getMessage());
      }
    }
    heads.keepRepositories(installationId, covered);
  }

  // --- pull request checks ------------------------------------------------------------------

  /** Validates servicedna.yaml and starts the flows at the pull request's head; results arrive via {@link #onSuiteFinished}. */
  void check(GitHubInstallation installation, JsonNode repo, String sha) {
    long installationId = installation.getInstallationId();
    String fullName = repo.path("full_name").asText();
    Map<String, Object> started = new LinkedHashMap<>();
    started.put("name", CHECK_NAME);
    started.put("head_sha", sha);
    started.put("status", "in_progress");
    long checkRunId = github.post(installationId, "/repos/" + fullName + "/check-runs", started).path("id").asLong();

    List<String> problems = new ArrayList<>();
    StringBuilder summary = new StringBuilder();
    Optional<String> manifest = manifest(installationId, fullName, sha);
    if (manifest.isPresent()) {
      GitHubImportService.ManifestCheck result = imports.check(repo, manifest.get());
      if (result.valid()) {
        summary.append("✅ `servicedna.yaml`: ").append(result.service()).append(" — ").append(result.summary()).append("\n\n");
      } else {
        problems.add("`servicedna.yaml`: " + result.summary());
      }
    }
    List<TestRunDto.StartSuite> flows = new ArrayList<>();
    for (String file : github.directory(installationId, fullName, "flows", sha)) {
      if (!file.endsWith(".yaml") && !file.endsWith(".yml")) {
        continue;
      }
      try {
        String text = github.file(installationId, fullName, "flows/" + file, sha).orElse("");
        flows.add(FlowFiles.parse(text, file));
      } catch (FlowFiles.InvalidFlowException e) {
        problems.add("`flows/" + e.getMessage() + "`");
      }
    }

    if (!problems.isEmpty()) {
      complete(installationId, fullName, checkRunId, "failure", "servicedna.yaml or flows need fixing",
          summary + "### ❌ Problems\n\n" + String.join("\n", problems.stream().map(p -> "- " + p).toList()));
      return;
    }
    if (flows.isEmpty()) {
      complete(installationId, fullName, checkRunId, "success", manifest.isPresent() ? "servicedna.yaml is valid" : "Nothing to check",
          summary.length() > 0 ? summary.toString() : "No `servicedna.yaml` or `flows/` in this repository.");
      return;
    }
    List<UUID> suiteIds = new ArrayList<>();
    try {
      for (TestRunDto.StartSuite flow : flows) {
        suiteIds.add(suites.start(installation.getOrganizationId(), flow, installation.getInstalledBy()).id());
      }
    } catch (ApiException e) {
      complete(installationId, fullName, checkRunId, "failure", "Couldn't start the flows", summary + "❌ " + e.getMessage());
      return;
    }
    transactions.executeWithoutResult(status -> checkRuns.save(new GitHubCheckRun(installationId, fullName, checkRunId, suiteIds, summary.toString())));
    // A suite may already have finished before the check was saved.
    suiteIds.forEach(id -> finishIfDone(id));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onSuiteFinished(SuiteFinishedEvent event) {
    worker.submit(() -> {
      try {
        finishIfDone(event.suiteId());
      } catch (RuntimeException e) {
        log.warn("Reporting suite {} to GitHub failed: {}", event.suiteId(), e.getMessage());
      }
    });
  }

  private void finishIfDone(UUID suiteId) {
    for (GitHubCheckRun run : checkRuns.findOpenWaitingFor(suiteId.toString())) {
      GitHubInstallation installation = installations.findById(run.getInstallationId()).orElse(null);
      if (installation == null) {
        continue;
      }
      List<TestRunDto.Suite> done = new ArrayList<>();
      for (UUID id : run.getSuiteIds()) {
        Optional<TestRunDto.Suite> suite = suites.find(installation.getOrganizationId(), id);
        if (suite.isEmpty() || "RUNNING".equals(suite.get().status())) {
          return; // still waiting for one of them
        }
        done.add(suite.get());
      }
      Integer claimed = transactions.execute(status -> checkRuns.markCompleted(run.getId()));
      if (claimed == null || claimed == 0) {
        continue; // another thread reported it
      }
      boolean passed = done.stream().allMatch(s -> "PASSED".equals(s.status()));
      int ok = done.stream().mapToInt(TestRunDto.Suite::passed).sum();
      int failed = done.stream().mapToInt(TestRunDto.Suite::failed).sum();
      complete(run.getInstallationId(), run.getRepository(), run.getCheckRunId(), passed ? "success" : "failure",
          ok + " passed, " + failed + " failed", (run.getSummary() == null ? "" : run.getSummary()) + report(done));
    }
  }

  private void complete(long installationId, String repository, long checkRunId, String conclusion, String title, String summary) {
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("title", title);
    output.put("summary", summary.length() > 60_000 ? summary.substring(0, 60_000) + "\n\n…" : summary);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "completed");
    body.put("conclusion", conclusion);
    body.put("output", output);
    github.patch(installationId, "/repos/" + repository + "/check-runs/" + checkRunId, body);
  }

  /** The flows' results as Markdown, each case linked to its trace — the same table `sdna test run --report` writes. */
  String report(List<TestRunDto.Suite> done) {
    StringBuilder b = new StringBuilder();
    for (TestRunDto.Suite s : done) {
      b.append("**").append(s.name()).append("**\n\n| | Case | Checks that failed |\n|---|---|---|\n");
      for (TestRunDto.Run run : s.runs()) {
        String mark = Boolean.TRUE.equals(run.passed()) ? "✅" : "❌";
        String name = run.caseName() == null ? "" : run.caseName().replace("|", "\\|");
        if (run.traceId() != null) {
          name = "[" + name + "](" + frontendUrl + "/traces?trace=" + run.traceId() + ")";
        }
        List<String> failedChecks = new ArrayList<>();
        if (run.assertionResults() != null) {
          run.assertionResults().forEach(r -> {
            if (!r.path("passed").asBoolean()) {
              failedChecks.add((r.path("description").asText() + " — " + r.path("message").asText()).replace("|", "\\|"));
            }
          });
        }
        b.append("| ").append(mark).append(" | ").append(name).append(" | ").append(String.join("<br>", failedChecks)).append(" |\n");
      }
      b.append("\n");
    }
    return b.toString();
  }

  // --- helpers ------------------------------------------------------------------------------

  String state(UUID organizationId, UUID userId, long expiresEpochSecond) {
    String payload = organizationId + "." + userId + "." + expiresEpochSecond;
    return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "."
        + HexFormat.of().formatHex(hmac(github.webhookSecret(), payload.getBytes(StandardCharsets.UTF_8)));
  }

  private void verifyState(String state, UUID organizationId, UUID userId) {
    String[] parts = state.split("\\.", 2);
    try {
      String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
      String[] fields = payload.split("\\.");
      boolean signed = parts.length == 2
          && MessageDigest.isEqual(hmac(github.webhookSecret(), payload.getBytes(StandardCharsets.UTF_8)), HexFormat.of().parseHex(parts[1]));
      if (signed && fields.length == 3 && fields[0].equals(organizationId.toString()) && fields[1].equals(userId.toString())
          && Long.parseLong(fields[2]) > Instant.now().getEpochSecond()) {
        return;
      }
    } catch (RuntimeException ignored) {
      // falls through
    }
    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GITHUB_STATE", "This GitHub setup link isn't valid for you any more; start again from Settings.");
  }

  private static String sha256(String text) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] hmac(String secret, byte[] data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(data);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private void requireAdmin(UUID organizationId, UUID userId) {
    if (!isAdmin(organizationService.validateUserAccess(organizationId, userId))) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Only owners and admins can connect GitHub.");
    }
  }

  private static boolean isAdmin(OrganizationMember member) {
    return member.getRole() == OrganizationRole.OWNER || member.getRole() == OrganizationRole.ADMIN;
  }

  private static Installation view(GitHubInstallation i) {
    return new Installation(i.getInstallationId(), i.getAccountLogin(), i.getInstalledAt(), i.getLastSyncedAt());
  }
}
