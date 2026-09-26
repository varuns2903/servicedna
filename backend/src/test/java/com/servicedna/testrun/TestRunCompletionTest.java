package com.servicedna.testrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.servicedna.graph.domain.Protocol;
import com.servicedna.traces.TraceDto;
import com.servicedna.traces.TraceQueryService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "test-runs.settle-ms=200", "test-runs.min-wait-ms=0", "test-runs.max-wait-ms=600",
    "test-runs.complete-check-ms=3600000", "test-runs.sweep-ms=3600000"})
@ActiveProfiles("test")
class TestRunCompletionTest {

  @Autowired private TestRunCompleter completer;
  @Autowired private TestRunRepository repository;
  @Autowired private com.servicedna.organization.repository.OrganizationRepository organizations;
  @MockBean private TraceQueryService traces;

  private static TraceDto.Span span(String id, String parent, String service, String name, String kind, Map<String, String> attributes) {
    return new TraceDto.Span(id, parent, service, name, kind, Instant.parse("2026-09-26T10:00:00Z"), 5, false, null, attributes, List.of());
  }

  @Test
  void hopsAreTheBoundariesWithWhatCrossedThem() {
    TraceDto.Trace trace = new TraceDto.Trace("t", Instant.now(), 20, List.of(
        span("1", null, "gateway", "POST /api/orders", "SERVER", Map.of("sdna.request.body", "{\"userId\":\"u-1\"}", "sdna.response.body", "{\"id\":\"o-1\"}", "http.response.status_code", "201")),
        span("2", "1", "gateway", "POST", "CLIENT", Map.of()),
        span("3", "2", "orders", "POST /orders", "SERVER", Map.of("sdna.request.body", "{}")),
        span("4", "3", "orders", "compute total", "INTERNAL", Map.of("sdna.capture.order.total", "59")),
        span("5", "3", "orders", "INSERT orders", "CLIENT", Map.of("db.statement", "INSERT INTO orders VALUES (?)"))));

    List<TestRunDto.Hop> hops = TestRunViews.hops(trace);

    assertThat(hops).extracting(TestRunDto.Hop::spanId).containsExactly("1", "3", "5");
    assertThat(hops.get(0).httpStatus()).isEqualTo(201);
    assertThat(hops.get(0).responseBody()).isEqualTo("{\"id\":\"o-1\"}");
    assertThat(hops.get(1).captured()).containsEntry("order.total", "59");
    assertThat(hops.get(2).requestBody()).isEqualTo("INSERT INTO orders VALUES (?)");
  }

  @Test
  void aRunCompletesOnceItsTraceStopsGrowing() throws Exception {
    AtomicReference<List<TraceDto.Span>> spans = new AtomicReference<>(new ArrayList<>());
    when(traces.trace(any(UUID.class), anyString())).thenAnswer(inv -> new TraceDto.Trace("t", Instant.now(), 1, spans.get()));
    UUID runId = waitingRun();

    spans.set(List.of(span("1", null, "gateway", "GET /", "SERVER", Map.of())));
    completer.completeSettledRuns();
    Thread.sleep(100);
    spans.set(List.of(span("1", null, "gateway", "GET /", "SERVER", Map.of()), span("2", "1", "orders", "GET /", "SERVER", Map.of())));
    completer.completeSettledRuns(); // grew: settle restarts
    assertThat(repository.findById(runId).orElseThrow().getStatus()).isEqualTo(TestRunStatus.WAITING);

    Thread.sleep(250);
    completer.completeSettledRuns();
    TestRun run = repository.findById(runId).orElseThrow();
    assertThat(run.getStatus()).isEqualTo(TestRunStatus.COMPLETED);
    assertThat(run.getError()).isNull();
  }

  @Test
  void aRunWithNoSpansCompletesAtTheLimitWithAnExplanation() throws Exception {
    when(traces.trace(any(UUID.class), anyString())).thenAnswer(inv -> new TraceDto.Trace("t", Instant.now(), 0, List.of()));
    UUID runId = waitingRun();

    completer.completeSettledRuns();
    assertThat(repository.findById(runId).orElseThrow().getStatus()).isEqualTo(TestRunStatus.WAITING);
    Thread.sleep(650);
    completer.completeSettledRuns();

    TestRun run = repository.findById(runId).orElseThrow();
    assertThat(run.getStatus()).isEqualTo(TestRunStatus.COMPLETED);
    assertThat(run.getError()).startsWith("No spans arrived");
  }

  private UUID waitingRun() {
    var org = organizations.save(new com.servicedna.organization.domain.Organization(UUID.randomUUID(), "Completion Org"));
    TestRun run = new TestRun(org.getId(), null, Protocol.HTTP, null, "{}", "{}", "4bf92f3577b34da6a3ce929d0e0e4736", null);
    run.setStatus(TestRunStatus.WAITING);
    run.setRespondedAt(OffsetDateTime.now());
    return repository.save(run).getId();
  }
}
