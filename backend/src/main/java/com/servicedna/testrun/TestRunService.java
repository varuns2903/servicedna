package com.servicedna.testrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.servicedna.common.exception.ApiException;
import com.servicedna.graph.domain.Protocol;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
import java.net.URI;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class TestRunService {

  /** How long a run may wait for a runner, or for the runner's result. */
  static final int PICKUP_TIMEOUT_SECONDS = 120;

  private final TestRunRepository repository;
  private final ServiceRepository serviceRepository;
  private final OrganizationService organizationService;
  private final ObjectMapper json;
  private final SecureRandom random = new SecureRandom();

  public TestRunService(
      TestRunRepository repository, ServiceRepository serviceRepository, OrganizationService organizationService, ObjectMapper json) {
    this.repository = repository;
    this.serviceRepository = serviceRepository;
    this.organizationService = organizationService;
    this.json = json;
  }

  @Transactional
  public TestRun create(UUID organizationId, TestRunDto.CreateRequest request, UUID userId) {
    OrganizationMember member = organizationService.validateUserAccess(organizationId, userId);
    if (member.getRole() == OrganizationRole.VIEWER) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Viewers can't send test requests.");
    }
    Service service = null;
    if (request.serviceId() != null) {
      service = serviceRepository.findByOrganizationIdAndId(organizationId, request.serviceId())
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));
    }
    String environment = blankToNull(request.environment());
    if (environment == null && service != null) {
      environment = service.getEnvironment();
    }

    ObjectNode target = json.createObjectNode();
    switch (request.protocol()) {
      case HTTP, GRAPHQL -> {
        requireService(service);
        String path = blankToNull(request.path());
        if (path == null || !path.startsWith("/") || path.contains("{")) {
          throw invalid("Give the concrete request path, e.g. /orders/o-17 (placeholders like {id} filled in).");
        }
        target.put("service", service.getName());
        target.put("method", request.method() == null ? "POST" : request.method().toUpperCase());
        target.put("path", path);
        baseUrl(service).ifPresent(url -> target.put("baseUrl", url));
      }
      case GRPC -> {
        requireService(service);
        if (blankToNull(request.grpcMethod()) == null || !request.grpcMethod().contains("/")) {
          throw invalid("gRPC runs need grpcMethod as package.Service/Method.");
        }
        target.put("service", service.getName());
        target.put("method", request.grpcMethod());
        baseUrl(service).map(URI::create).ifPresent(u -> target.put("address", u.getHost() + ":" + (u.getPort() > 0 ? u.getPort() : 443)));
      }
      case MESSAGING -> {
        if (blankToNull(request.topic()) == null) {
          throw invalid("Messaging runs need a topic.");
        }
        target.put("topic", request.topic());
      }
      default -> throw invalid("Test runs support HTTP, GraphQL, gRPC and messaging.");
    }

    ObjectNode body = json.createObjectNode();
    body.set("headers", json.valueToTree(request.headers() == null ? Map.of() : request.headers()));
    body.put("body", request.body() == null ? "" : request.body());
    if (request.key() != null) {
      body.put("key", request.key());
    }
    byte[] traceId = new byte[16];
    random.nextBytes(traceId);
    return repository.save(
        new TestRun(organizationId, environment, request.protocol(), service != null ? service.getId() : null,
            target.toString(), body.toString(), HexFormat.of().formatHex(traceId), userId));
  }

  @Transactional(readOnly = true)
  public TestRun get(UUID organizationId, UUID runId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    return repository.findByOrganizationIdAndId(organizationId, runId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEST_RUN_NOT_FOUND", "Test run not found"));
  }

  @Transactional(readOnly = true)
  public List<TestRun> list(UUID organizationId, int limit, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    return repository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, PageRequest.of(0, Math.max(1, Math.min(limit, 200))));
  }

  /**
   * Hands the oldest queued run a runner may take to that runner, or empty. {@code organizationId}
   * null means a shared runner serving every organization on this ServiceDNA.
   */
  @Transactional
  public Optional<TestRun> claim(UUID organizationId, String environment, String runner) {
    for (UUID id : repository.findClaimable(organizationId, blankToNull(environment), PageRequest.of(0, 5))) {
      if (repository.claim(id, runner, OffsetDateTime.now()) == 1) {
        return repository.findById(id);
      }
    }
    return Optional.empty();
  }

  @Transactional
  public TestRun report(UUID organizationId, UUID runId, TestRunDto.JobResult result) {
    TestRun run = repository.findById(runId)
        .filter(r -> organizationId == null || r.getOrganizationId().equals(organizationId))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEST_RUN_NOT_FOUND", "Test run not found"));
    if (run.getStatus() != TestRunStatus.RUNNING) {
      throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Run is " + run.getStatus());
    }
    run.setRespondedAt(OffsetDateTime.now());
    run.setResult(json.valueToTree(result).toString());
    if (result.sent()) {
      run.setStatus(TestRunStatus.WAITING);
    } else {
      run.setStatus(TestRunStatus.FAILED);
      run.setError(result.error());
      run.setFinishedAt(OffsetDateTime.now());
    }
    return run;
  }

  /** Runs nobody picked up (no runner in that environment) or never reported back. */
  @Scheduled(fixedDelayString = "${test-runs.sweep-ms:10000}")
  @Transactional
  public void expireStale() {
    OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(PICKUP_TIMEOUT_SECONDS);
    for (TestRun run : repository.findByStatusInAndCreatedAtBefore(EnumSet.of(TestRunStatus.QUEUED, TestRunStatus.RUNNING), cutoff)) {
      run.setStatus(TestRunStatus.TIMED_OUT);
      run.setError(run.getRunner() == null
          ? "No runner picked this run up" + (run.getEnvironment() != null ? " in " + run.getEnvironment() : "") + ". Is a ServiceDNA runner deployed there?"
          : "Runner " + run.getRunner() + " didn't report a result.");
      run.setFinishedAt(OffsetDateTime.now());
    }
  }

  public JsonNode readJson(String text) {
    try {
      return text == null ? null : json.readTree(text);
    } catch (Exception e) {
      return json.getNodeFactory().textNode(text);
    }
  }

  /** Where a service is reachable: the origin of its health-check URL (runners can override). */
  static Optional<String> baseUrl(Service service) {
    if (service.getHealthCheckUrl() == null) {
      return Optional.empty();
    }
    try {
      URI u = URI.create(service.getHealthCheckUrl());
      return Optional.of(u.getScheme() + "://" + u.getRawAuthority());
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static void requireService(Service service) {
    if (service == null) {
      throw invalid("Choose the service to call (serviceId).");
    }
  }

  private static ApiException invalid(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TEST_RUN", message);
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
