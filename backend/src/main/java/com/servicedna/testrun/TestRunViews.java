package com.servicedna.testrun;

import com.servicedna.common.exception.ApiException;
import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.traces.TraceDto;
import com.servicedna.traces.TraceQueryService;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Turns runs into API views; the detail view assembles the run's hops from its trace. */
@Component
public class TestRunViews {

  private final TestRunService testRunService;
  private final ServiceRepository serviceRepository;
  private final TraceQueryService traces;

  public TestRunViews(TestRunService testRunService, ServiceRepository serviceRepository, TraceQueryService traces) {
    this.testRunService = testRunService;
    this.serviceRepository = serviceRepository;
    this.traces = traces;
  }

  public TestRunDto.Run summary(TestRun run) {
    return view(run, null);
  }

  /** With hops once the runner has responded (they fill in as spans arrive). */
  public TestRunDto.Run detail(TestRun run) {
    if (!EnumSet.of(TestRunStatus.WAITING, TestRunStatus.COMPLETED).contains(run.getStatus())) {
      return view(run, null);
    }
    try {
      return view(run, hops(traces.trace(run.getOrganizationId(), run.getTraceId())));
    } catch (ApiException e) {
      return view(run, List.of());
    }
  }

  /**
   * A hop is a span where something crossed a boundary: every server/consumer span (a service
   * handling the request), and client/producer spans that nothing instrumented answered
   * (databases, external APIs, topics) — each with what went in and came out.
   */
  static List<TestRunDto.Hop> hops(TraceDto.Trace trace) {
    Map<String, TraceDto.Span> byId = new LinkedHashMap<>();
    trace.spans().forEach(s -> byId.put(s.spanId(), s));
    java.util.Set<String> answered = new java.util.HashSet<>();
    for (TraceDto.Span s : trace.spans()) {
      if (s.parentSpanId() != null && ("SERVER".equals(s.kind()) || "CONSUMER".equals(s.kind()))) {
        answered.add(s.parentSpanId());
      }
    }
    List<TestRunDto.Hop> hops = new ArrayList<>();
    for (TraceDto.Span s : trace.spans()) {
      boolean inbound = "SERVER".equals(s.kind()) || "CONSUMER".equals(s.kind());
      boolean unanswered = ("CLIENT".equals(s.kind()) || "PRODUCER".equals(s.kind()))
          && !answered.contains(s.spanId())
          && isCall(s.attributes());
      if (!inbound && !unanswered) {
        continue;
      }
      Map<String, String> a = s.attributes();
      Map<String, String> captured = new LinkedHashMap<>();
      a.forEach((k, v) -> {
        if (k.startsWith("sdna.capture.")) {
          captured.put(k.substring("sdna.capture.".length()), v);
        }
      });
      // capture() values recorded on internal spans belong to the enclosing hop's service.
      hops.add(new TestRunDto.Hop(
          s.spanId(), s.parentSpanId(), s.service(), s.name(), s.kind(),
          s.start().atOffset(ZoneOffset.UTC), s.durationMs(), s.error(), s.statusMessage(),
          httpStatus(a), firstNonNull(a.get("sdna.request.body"), statement(a)), a.get("sdna.response.body"), captured));
    }
    attachInternalCaptures(trace, byId, hops);
    return hops;
  }

  private static void attachInternalCaptures(TraceDto.Trace trace, Map<String, TraceDto.Span> byId, List<TestRunDto.Hop> hops) {
    Map<String, TestRunDto.Hop> hopBySpan = new LinkedHashMap<>();
    hops.forEach(h -> hopBySpan.put(h.spanId(), h));
    for (TraceDto.Span s : trace.spans()) {
      if (hopBySpan.containsKey(s.spanId())) {
        continue;
      }
      for (Map.Entry<String, String> e : s.attributes().entrySet()) {
        if (!e.getKey().startsWith("sdna.capture.")) {
          continue;
        }
        TraceDto.Span ancestor = s.parentSpanId() != null ? byId.get(s.parentSpanId()) : null;
        while (ancestor != null && !hopBySpan.containsKey(ancestor.spanId())) {
          ancestor = ancestor.parentSpanId() != null ? byId.get(ancestor.parentSpanId()) : null;
        }
        if (ancestor != null) {
          hopBySpan.get(ancestor.spanId()).captured().put(e.getKey().substring("sdna.capture.".length()), e.getValue());
        }
      }
    }
  }

  /** A network call worth showing (not, say, a DNS lookup): HTTP, RPC, database or messaging. */
  private static boolean isCall(Map<String, String> a) {
    for (String key : List.of("http.request.method", "http.method", "rpc.system", "db.system", "db.system.name", "messaging.system")) {
      if (a.containsKey(key)) {
        return true;
      }
    }
    return false;
  }

  private static Integer httpStatus(Map<String, String> a) {
    String v = firstNonNull(a.get("http.response.status_code"), a.get("http.status_code"), a.get("rpc.grpc.status_code"));
    try {
      return v == null ? null : Integer.valueOf(v);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** For database hops, the query is what went in. */
  private static String statement(Map<String, String> a) {
    return firstNonNull(a.get("db.query.text"), a.get("db.statement"));
  }

  @SafeVarargs
  private static <T> T firstNonNull(T... values) {
    for (T v : values) {
      if (v != null) {
        return v;
      }
    }
    return null;
  }

  TestRunDto.Run view(TestRun run, List<TestRunDto.Hop> hops) {
    String serviceName = run.getTargetServiceId() == null ? null
        : serviceRepository.findById(run.getTargetServiceId()).map(Service::getName).orElse(null);
    return new TestRunDto.Run(
        run.getId(), run.getEnvironment(), run.getProtocol(), run.getTargetServiceId(), serviceName,
        testRunService.readJson(run.getTarget()), testRunService.readJson(run.getRequest()), run.getTraceId(),
        run.getStatus(), testRunService.readJson(run.getResult()), run.getError(), run.getRunner(),
        run.getCreatedAt(), run.getFinishedAt(), run.getCaseName(), run.getPassed(),
        testRunService.readJson(run.getAssertionResults()), hops);
  }
}
