package com.servicedna.testrun;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.traces.TraceQueryService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/** Collections of test cases, and suites that run them and check their assertions. */
@org.springframework.stereotype.Service
public class TestSuiteService {

  private static final Logger log = LoggerFactory.getLogger(TestSuiteService.class);
  private static final Set<TestRunStatus> FINISHED = EnumSet.of(TestRunStatus.COMPLETED, TestRunStatus.FAILED, TestRunStatus.TIMED_OUT);

  private final TestCollectionRepository collections;
  private final TestSuiteRepository suites;
  private final TestRunRepository runs;
  private final TestRunService testRunService;
  private final TestRunViews views;
  private final TraceQueryService traces;
  private final OrganizationService organizationService;
  private final ObjectMapper json;
  private final AssertionEvaluator evaluator;

  public TestSuiteService(
      TestCollectionRepository collections,
      TestSuiteRepository suites,
      TestRunRepository runs,
      TestRunService testRunService,
      TestRunViews views,
      TraceQueryService traces,
      OrganizationService organizationService,
      ObjectMapper json) {
    this.collections = collections;
    this.suites = suites;
    this.runs = runs;
    this.testRunService = testRunService;
    this.views = views;
    this.traces = traces;
    this.organizationService = organizationService;
    this.json = json;
    this.evaluator = new AssertionEvaluator(json);
  }

  // --- collections -------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<TestRunDto.Collection> listCollections(UUID organizationId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    return collections.findByOrganizationIdOrderByName(organizationId).stream().map(this::toDto).toList();
  }

  @Transactional
  public TestRunDto.Collection saveCollection(UUID organizationId, UUID collectionId, TestRunDto.SaveCollection request, UUID userId) {
    requireEditor(organizationId, userId);
    TestCollection collection = collectionId == null
        ? new TestCollection(organizationId, userId)
        : collections.findByOrganizationIdAndId(organizationId, collectionId).orElseThrow(TestSuiteService::collectionNotFound);
    collections.findByOrganizationIdAndName(organizationId, request.name())
        .filter(other -> !other.getId().equals(collection.getId()))
        .ifPresent(other -> { throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_EXISTS", "A collection named " + request.name() + " exists"); });
    collection.setName(request.name());
    collection.setDescription(request.description());
    collection.setCases(json.valueToTree(request.cases()).toString());
    return toDto(collections.save(collection));
  }

  @Transactional
  public void deleteCollection(UUID organizationId, UUID collectionId, UUID userId) {
    requireEditor(organizationId, userId);
    collections.delete(collections.findByOrganizationIdAndId(organizationId, collectionId).orElseThrow(TestSuiteService::collectionNotFound));
  }

  // --- suites ------------------------------------------------------------------------------

  /** Queues one run per case; assertions are checked as the runs finish. */
  @Transactional
  public TestRunDto.Suite start(UUID organizationId, TestRunDto.StartSuite request, UUID userId) {
    requireEditor(organizationId, userId);
    List<TestRunDto.Case> cases;
    String name;
    if (request.collectionId() != null) {
      TestCollection collection = collections.findByOrganizationIdAndId(organizationId, request.collectionId()).orElseThrow(TestSuiteService::collectionNotFound);
      cases = parseCases(collection.getCases());
      name = request.name() != null ? request.name() : collection.getName();
    } else if (request.cases() != null && !request.cases().isEmpty()) {
      cases = request.cases();
      name = request.name() != null ? request.name() : "Ad-hoc suite";
    } else {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUITE", "Give a collectionId or cases.");
    }
    TestSuite suite = suites.save(new TestSuite(organizationId, name, request.collectionId(), request.environment(), userId));
    for (TestRunDto.Case c : cases) {
      TestRunDto.CreateRequest r = c.request();
      if (request.environment() != null && r.environment() == null) {
        r = new TestRunDto.CreateRequest(request.environment(), r.protocol(), r.serviceId(), r.serviceName(), r.method(), r.path(),
            r.grpcMethod(), r.topic(), r.key(), r.headers(), r.body(), r.testMode());
      }
      TestRun run = testRunService.create(organizationId, r, userId);
      run.setSuiteId(suite.getId());
      run.setCaseName(c.name());
      run.setAssertions(c.assertions() == null ? null : c.assertions().toString());
    }
    return toDto(suite);
  }

  @Transactional(readOnly = true)
  public TestRunDto.Suite get(UUID organizationId, UUID suiteId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    return toDto(suites.findByOrganizationIdAndId(organizationId, suiteId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUITE_NOT_FOUND", "Suite not found")));
  }

  @Transactional(readOnly = true)
  public List<TestRunDto.Suite> list(UUID organizationId, int limit, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    return suites.findByOrganizationIdOrderByCreatedAtDesc(organizationId, PageRequest.of(0, Math.max(1, Math.min(limit, 100))))
        .stream().map(this::toDto).toList();
  }

  /** Checks finished runs' assertions, then closes suites whose runs are all decided. */
  @Scheduled(fixedDelayString = "${test-runs.evaluate-ms:2000}")
  @Transactional
  public void evaluate() {
    for (TestSuite suite : suites.findByStatus(TestSuite.RUNNING)) {
      List<TestRun> suiteRuns = runs.findBySuiteIdOrderByCreatedAt(suite.getId());
      boolean allDecided = true;
      for (TestRun run : suiteRuns) {
        if (run.getPassed() == null && FINISHED.contains(run.getStatus())) {
          evaluate(run);
        }
        allDecided &= run.getPassed() != null;
      }
      if (allDecided) {
        suite.setStatus(suiteRuns.stream().allMatch(TestRun::getPassed) ? TestSuite.PASSED : TestSuite.FAILED);
        suite.setFinishedAt(OffsetDateTime.now());
      }
    }
  }

  void evaluate(TestRun run) {
    if (run.getStatus() != TestRunStatus.COMPLETED) {
      run.setPassed(false);
      run.setAssertionResults(json.valueToTree(List.of(new AssertionEvaluator.Result("run completed", false,
          run.getStatus() + (run.getError() != null ? ": " + run.getError() : "")))).toString());
      return;
    }
    List<TestRunDto.Hop> hops;
    try {
      hops = TestRunViews.hops(traces.trace(run.getOrganizationId(), run.getTraceId()));
    } catch (ApiException e) {
      log.warn("Trace for run {} unavailable: {}", run.getId(), e.getMessage());
      hops = List.of();
    }
    List<AssertionEvaluator.Result> results = evaluator.evaluate(testRunService.readJson(run.getAssertions()), testRunService.readJson(run.getResult()), hops);
    run.setAssertionResults(json.valueToTree(results).toString());
    run.setPassed(results.stream().allMatch(AssertionEvaluator.Result::passed));
  }

  private TestRunDto.Suite toDto(TestSuite suite) {
    List<TestRunDto.Run> suiteRuns = new ArrayList<>();
    int passed = 0, failed = 0, pending = 0;
    for (TestRun run : runs.findBySuiteIdOrderByCreatedAt(suite.getId())) {
      suiteRuns.add(views.summary(run));
      if (run.getPassed() == null) {
        pending++;
      } else if (run.getPassed()) {
        passed++;
      } else {
        failed++;
      }
    }
    return new TestRunDto.Suite(suite.getId(), suite.getName(), suite.getCollectionId(), suite.getEnvironment(), suite.getStatus(),
        suite.getCreatedAt(), suite.getFinishedAt(), passed, failed, pending, suiteRuns);
  }

  private TestRunDto.Collection toDto(TestCollection c) {
    return new TestRunDto.Collection(c.getId(), c.getName(), c.getDescription(), parseCases(c.getCases()), c.getUpdatedAt());
  }

  private List<TestRunDto.Case> parseCases(String text) {
    try {
      return json.readValue(text, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Unreadable collection cases", e);
    }
  }

  private void requireEditor(UUID organizationId, UUID userId) {
    OrganizationMember member = organizationService.validateUserAccess(organizationId, userId);
    if (member.getRole() == OrganizationRole.VIEWER) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Viewers can't run or edit tests.");
    }
  }

  private static ApiException collectionNotFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "COLLECTION_NOT_FOUND", "Collection not found");
  }
}
