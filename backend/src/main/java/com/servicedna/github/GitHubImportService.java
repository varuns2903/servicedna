package com.servicedna.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.IntegrationType;
import com.servicedna.alert.dto.CreateAlertRuleRequest;
import com.servicedna.catalog.dto.CatalogDto;
import com.servicedna.catalog.service.CatalogService;
import com.servicedna.common.exception.ApiException;
import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.repository.ServiceRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * First-time setup from GitHub: lists a GitHub organization's (or user's) repositories, reads each
 * one's servicedna.yaml, and registers the chosen ones as services — the same as running
 * `sdna scan` in each. The GitHub token is used for the request only, never stored.
 */
@Service
public class GitHubImportService {

  private static final int MAX_REPOS = 300;
  private static final ObjectMapper YAML =
      new ObjectMapper(new YAMLFactory()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

  private final String apiUrl;
  private final CatalogService catalog;
  private final ServiceRepository services;
  private final OrganizationService organizationService;
  private final Validator validator;
  private final ObjectMapper json;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public GitHubImportService(@Value("${github.api-url:https://api.github.com}") String apiUrl, CatalogService catalog,
      ServiceRepository services, OrganizationService organizationService, Validator validator, ObjectMapper json) {
    this.apiUrl = apiUrl.replaceAll("/+$", "");
    this.catalog = catalog;
    this.services = services;
    this.organizationService = organizationService;
    this.validator = validator;
    this.json = json;
  }

  public record Source(String owner, String token) {}

  public record ImportRequest(String owner, String token, List<String> repositories) {}

  /** A repository as the import screen shows it. */
  public record Repository(String fullName, String name, String url, String description, boolean archived,
      boolean hasManifest, String manifestError, String service, String owner, String tier, boolean registered) {}

  public record Imported(String repository, String service, boolean ok, String message) {}

  /** servicedna.yaml, as `sdna scan` reads it. */
  record Manifest(String service, String description, String owner, String tier, Double slo, String health, String repository,
      List<String> dependencies, List<Rule> alerts) {}

  record Rule(String condition, @JsonProperty("open_incident") String openIncident, String webhook, String integration,
      Double threshold, @JsonProperty("window_minutes") Integer windowMinutes) {}

  public List<Repository> preview(UUID organizationId, Source source, UUID userId) {
    requireEditor(organizationId, userId);
    Set<String> registered = services.findByOrganizationId(organizationId).stream()
        .map(s -> s.getName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    List<Repository> result = new ArrayList<>();
    for (JsonNode repo : repositories(source)) {
      String name = repo.path("name").asText();
      Manifest manifest = null;
      String error = null;
      boolean has = false;
      if (!repo.path("archived").asBoolean()) {
        try {
          String text = manifestText(source, repo.path("full_name").asText());
          has = text != null;
          manifest = text == null ? null : parse(text);
        } catch (ApiException e) {
          error = e.getMessage();
        }
      }
      String service = manifest != null && manifest.service() != null ? manifest.service() : name;
      result.add(new Repository(repo.path("full_name").asText(), name, repo.path("html_url").asText(),
          repo.path("description").asText(null), repo.path("archived").asBoolean(), has, error, service,
          manifest == null ? null : manifest.owner(), manifest == null ? null : manifest.tier(),
          registered.contains(service.toLowerCase(Locale.ROOT))));
    }
    return result;
  }

  /** Registers the chosen repositories' services; each one succeeds or fails on its own. */
  public List<Imported> importRepositories(UUID organizationId, ImportRequest request, UUID userId) {
    requireEditor(organizationId, userId);
    if (request.repositories() == null || request.repositories().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "NOTHING_TO_IMPORT", "Choose the repositories to import.");
    }
    Source source = new Source(request.owner(), request.token());
    List<Imported> results = new ArrayList<>();
    for (String fullName : request.repositories().stream().distinct().toList()) {
      String service = fullName.substring(fullName.indexOf('/') + 1);
      try {
        if (!fullName.matches("[\\w.-]+/[\\w.-]+")) {
          throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPOSITORY", "Not a repository name.");
        }
        JsonNode repo = get(source, "/repos/" + fullName);
        Imported imported = apply(organizationId, repo, manifestText(source, fullName), userId);
        results.add(imported);
      } catch (ApiException e) {
        results.add(new Imported(fullName, service, false, e.getMessage()));
      }
    }
    return results;
  }

  /** Registers (or updates) a repository's service from its servicedna.yaml text, if it has one. */
  public Imported apply(UUID organizationId, JsonNode repo, String manifestText, UUID userId) {
    Manifest m = manifestText == null ? null : parse(manifestText);
    CatalogDto.ScanRequest scan = validated(toScan(repo, m));
    CatalogDto.ScanResult applied = catalog.applyScan(organizationId, scan, userId);
    String message = m == null ? "registered (no servicedna.yaml)"
        : "registered from servicedna.yaml" + (applied.alertRules() != null ? ", " + applied.alertRules() + (applied.alertRules() == 1 ? " alert rule" : " alert rules") : "")
            + (applied.dependenciesUnknown().isEmpty() ? "" : "; not registered yet: " + String.join(", ", applied.dependenciesUnknown()));
    return new Imported(repo.path("full_name").asText(), scan.service(), true, message);
  }

  /** What a servicedna.yaml would set, or why it's invalid (for pull request checks). */
  public record ManifestCheck(boolean valid, String service, String summary) {}

  public ManifestCheck check(JsonNode repo, String manifestText) {
    try {
      Manifest m = parse(manifestText);
      CatalogDto.ScanRequest scan = validated(toScan(repo, m));
      List<String> parts = new ArrayList<>();
      if (m.owner() != null) parts.add("owner " + m.owner());
      if (m.tier() != null) parts.add("tier " + m.tier());
      if (m.slo() != null) parts.add("SLO " + m.slo() + "%");
      if (m.dependencies() != null && !m.dependencies().isEmpty()) parts.add("depends on " + String.join(", ", m.dependencies()));
      if (scan.alerts() != null) parts.add(scan.alerts().size() + (scan.alerts().size() == 1 ? " alert rule" : " alert rules"));
      return new ManifestCheck(true, scan.service(), parts.isEmpty() ? "no settings" : String.join(" · ", parts));
    } catch (ApiException e) {
      return new ManifestCheck(false, null, e.getMessage());
    }
  }

  private CatalogDto.ScanRequest validated(CatalogDto.ScanRequest scan) {
    Set<ConstraintViolation<CatalogDto.ScanRequest>> problems = validator.validate(scan);
    if (!problems.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MANIFEST",
          problems.stream().map(p -> p.getPropertyPath() + " " + p.getMessage()).sorted().collect(Collectors.joining("; ")));
    }
    return scan;
  }

  static CatalogDto.ScanRequest toScan(JsonNode repo, Manifest m) {
    String name = m != null && m.service() != null ? m.service() : repo.path("name").asText();
    String description = m != null && m.description() != null ? m.description() : repo.path("description").asText(null);
    CatalogDto.Metadata metadata = new CatalogDto.Metadata(description, m == null ? null : m.owner(), m == null ? null : m.tier(),
        m == null ? null : m.slo(), m == null ? null : m.health(),
        m != null && m.repository() != null ? m.repository() : repo.path("html_url").asText(null));
    List<CreateAlertRuleRequest> alerts = m == null || m.alerts() == null ? null : m.alerts().stream().map(r -> new CreateAlertRuleRequest(
        enumOf(AlertCondition.class, r.condition(), "condition"), r.webhook(),
        r.integration() == null ? null : enumOf(IntegrationType.class, r.integration(), "integration"),
        r.openIncident() == null ? null : enumOf(IncidentSeverity.class, r.openIncident(), "open_incident"),
        r.threshold(), r.windowMinutes())).toList();
    return new CatalogDto.ScanRequest(name, null, null, m == null || m.dependencies() == null ? List.of() : m.dependencies(), metadata, alerts);
  }

  static Manifest parse(String text) {
    try {
      Manifest m = YAML.readValue(text, Manifest.class);
      if (m == null) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MANIFEST", "servicedna.yaml is empty.");
      }
      return m;
    } catch (UnrecognizedPropertyException e) {
      String known = e.getKnownPropertyIds().stream().map(String::valueOf).sorted().collect(Collectors.joining(", "));
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MANIFEST",
          "servicedna.yaml: unknown field \"" + e.getPropertyName() + "\" (known: " + known + ")");
    } catch (IOException e) {
      String why = e.getMessage() == null ? "unreadable" : e.getMessage().lines().findFirst().orElse("unreadable");
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MANIFEST", "servicedna.yaml: " + why.replaceAll(" \\(class [\\w.$]+\\)", ""));
    }
  }


  private static <E extends Enum<E>> E enumOf(Class<E> type, String value, String field) {
    try {
      return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    } catch (RuntimeException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MANIFEST", "servicedna.yaml: unknown " + field + " " + value);
    }
  }

  private List<JsonNode> repositories(Source source) {
    if (source.owner() == null || !source.owner().matches("[\\w.-]+")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GITHUB_OWNER", "Give the GitHub organization or user.");
    }
    List<JsonNode> repos = new ArrayList<>();
    String base = "/orgs/" + source.owner() + "/repos";
    for (int page = 1; repos.size() < MAX_REPOS; page++) {
      JsonNode batch;
      try {
        batch = get(source, base + "?per_page=100&type=all&page=" + page);
      } catch (ApiException e) {
        if (page == 1 && e.getStatus() == HttpStatus.NOT_FOUND && base.startsWith("/orgs/")) {
          base = "/users/" + source.owner() + "/repos"; // a personal account, not an organization
          page = 0;
          continue;
        }
        throw e;
      }
      batch.forEach(repos::add);
      if (batch.size() < 100) {
        break;
      }
    }
    return repos;
  }

  /** The repository's servicedna.yaml (or .yml) on its default branch, or null. */
  private String manifestText(Source source, String fullName) {
    for (String file : List.of("servicedna.yaml", "servicedna.yml")) {
      try {
        JsonNode content = get(source, "/repos/" + fullName + "/contents/" + URLEncoder.encode(file, StandardCharsets.UTF_8));
        return new String(Base64.getMimeDecoder().decode(content.path("content").asText()), StandardCharsets.UTF_8);
      } catch (ApiException e) {
        if (e.getStatus() != HttpStatus.NOT_FOUND) {
          throw e;
        }
      }
    }
    return null;
  }

  private JsonNode get(Source source, String path) {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(apiUrl + path))
        .timeout(Duration.ofSeconds(20))
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .GET();
    if (source.token() != null && !source.token().isBlank()) {
      request.header("Authorization", "Bearer " + source.token().trim());
    }
    try {
      HttpResponse<String> res = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
      switch (res.statusCode()) {
        case 200 -> {
          return json.readTree(res.body());
        }
        case 401 -> throw new ApiException(HttpStatus.BAD_REQUEST, "GITHUB_TOKEN_REJECTED", "GitHub rejected the token.");
        case 403, 429 -> throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "GITHUB_RATE_LIMITED",
            "GitHub refused the request (rate limit or missing permission); use a token with read access to the repositories.");
        case 404 -> throw new ApiException(HttpStatus.NOT_FOUND, "GITHUB_NOT_FOUND", "Not found on GitHub (or the token can't see it).");
        default -> throw new ApiException(HttpStatus.BAD_GATEWAY, "GITHUB_ERROR", "GitHub returned HTTP " + res.statusCode());
      }
    } catch (IOException e) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "GITHUB_ERROR", "GitHub unreachable.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.BAD_GATEWAY, "GITHUB_ERROR", "Interrupted.");
    }
  }

  private void requireEditor(UUID organizationId, UUID userId) {
    OrganizationMember member = organizationService.validateUserAccess(organizationId, userId);
    if (member.getRole() == OrganizationRole.VIEWER) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Viewers can't import services.");
    }
  }
}
