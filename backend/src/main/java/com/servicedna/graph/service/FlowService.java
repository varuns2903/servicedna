package com.servicedna.graph.service;

import com.servicedna.graph.domain.ObservedCall;
import com.servicedna.graph.domain.Protocol;
import com.servicedna.graph.domain.TargetKind;
import com.servicedna.graph.dto.FlowDto;
import com.servicedna.graph.repository.ObservedCallRepository;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/** Operation-level views over observed calls: the org-wide flow map and per-entry flowcharts. */
@org.springframework.stereotype.Service
public class FlowService {

  private final ObservedCallRepository observedCallRepository;
  private final ServiceRepository serviceRepository;
  private final OrganizationService organizationService;

  public FlowService(
      ObservedCallRepository observedCallRepository,
      ServiceRepository serviceRepository,
      OrganizationService organizationService) {
    this.observedCallRepository = observedCallRepository;
    this.serviceRepository = serviceRepository;
    this.organizationService = organizationService;
  }

  /**
   * The flow graph over the window. With an entry (service node + operation), only what that
   * operation triggers, followed downstream through each callee's operation; without one, every
   * observed call.
   */
  @Transactional(readOnly = true)
  public FlowDto flows(UUID organizationId, int windowMinutes, String entryNode, String entryOperation, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    Window w = window(organizationId, windowMinutes);

    Map<String, Totals> edges = new LinkedHashMap<>();
    for (ObservedCall call : w.calls) {
      String source = opId(call.getSourceServiceId().toString(), call.getSourceOperation());
      String target = opId(GraphService.targetId(call), call.getTargetOperation());
      edges.computeIfAbsent(source + "->" + target + "|" + call.getProtocol(), k -> new Totals(source, target, call.getProtocol())).add(call);
    }

    Set<String> included;
    FlowDto.Entry entry = null;
    if (entryNode != null && entryOperation != null) {
      entry = new FlowDto.Entry(entryNode, entryOperation);
      included = downstreamOf(opId(entryNode, entryOperation), edges.values());
    } else {
      included = null;
    }

    Map<String, FlowDto.Operation> operations = new LinkedHashMap<>();
    List<FlowDto.Call> calls = new ArrayList<>();
    for (Totals t : edges.values()) {
      if (included != null && !(included.contains(t.source) && included.contains(t.target))) {
        continue;
      }
      calls.add(t.toDto(w.minutes));
      addOperation(operations, t.source, w, 0);
      addOperation(operations, t.target, w, t.calls);
    }
    if (entry != null) {
      addOperation(operations, opId(entryNode, entryOperation), w, 0);
    }
    calls.sort(Comparator.comparingLong(FlowDto.Call::calls).reversed().thenComparing(FlowDto.Call::source).thenComparing(FlowDto.Call::target));
    return new FlowDto(w.since, entry, new ArrayList<>(operations.values()), calls);
  }

  /**
   * Operations to start a flowchart from, busiest first: service operations that no instrumented
   * service calls (their callers are outside, e.g. browsers or cron), plus the entry operations of
   * jobs that make calls.
   */
  @Transactional(readOnly = true)
  public List<FlowDto.EntryPoint> entryPoints(UUID organizationId, int windowMinutes, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    Window w = window(organizationId, windowMinutes);

    Set<String> called = new HashSet<>();
    Map<String, Long> outgoing = new HashMap<>();
    for (ObservedCall call : w.calls) {
      if (call.getTargetKind() == TargetKind.SERVICE) {
        called.add(opId(call.getTargetServiceId().toString(), call.getTargetOperation()));
      }
      if (!call.getSourceOperation().isEmpty()) {
        outgoing.merge(opId(call.getSourceServiceId().toString(), call.getSourceOperation()), call.getCalls(), Long::sum);
      }
    }
    List<FlowDto.EntryPoint> entries = new ArrayList<>();
    outgoing.forEach((id, calls) -> {
      if (!called.contains(id)) {
        String[] parts = id.split("\\|", 2);
        entries.add(new FlowDto.EntryPoint(parts[0], w.names.getOrDefault(parts[0], parts[0]), parts[1], calls));
      }
    });
    entries.sort(Comparator.comparingLong(FlowDto.EntryPoint::calls).reversed());
    return entries;
  }

  private Window window(UUID organizationId, int windowMinutes) {
    int minutes = Math.max(1, Math.min(windowMinutes, GraphService.MAX_WINDOW_MINUTES));
    OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES).minusMinutes(minutes);
    Map<String, String> names =
        serviceRepository.findByOrganizationId(organizationId).stream()
            .collect(Collectors.toMap(s -> s.getId().toString(), Service::getName));
    return new Window(minutes, since, observedCallRepository.findByOrganizationIdAndBucketStartGreaterThanEqual(organizationId, since), names);
  }

  private record Window(int minutes, OffsetDateTime since, List<ObservedCall> calls, Map<String, String> names) {}

  private static Set<String> downstreamOf(String start, Iterable<Totals> edges) {
    Map<String, List<String>> next = new HashMap<>();
    for (Totals t : edges) {
      next.computeIfAbsent(t.source, k -> new ArrayList<>()).add(t.target);
    }
    Set<String> seen = new HashSet<>(Set.of(start));
    Deque<String> queue = new ArrayDeque<>(List.of(start));
    while (!queue.isEmpty()) {
      for (String target : next.getOrDefault(queue.poll(), List.of())) {
        if (seen.add(target)) {
          queue.add(target);
        }
      }
    }
    return seen;
  }

  private static void addOperation(Map<String, FlowDto.Operation> operations, String id, Window w, long callsIn) {
    String[] parts = id.split("\\|", 2);
    String nodeId = parts[0];
    TargetKind kind = nodeId.contains(":") ? TargetKind.valueOf(nodeId.substring(0, nodeId.indexOf(':'))) : TargetKind.SERVICE;
    String name = kind == TargetKind.SERVICE ? w.names.getOrDefault(nodeId, nodeId) : nodeId.substring(nodeId.indexOf(':') + 1);
    FlowDto.Operation existing = operations.get(id);
    operations.put(id, new FlowDto.Operation(id, nodeId, name, kind, parts[1], (existing != null ? existing.callsIn() : 0) + callsIn));
  }

  static String opId(String nodeId, String operation) {
    return nodeId + "|" + operation;
  }

  private static final class Totals {
    final String source;
    final String target;
    final Protocol protocol;
    long calls;
    long errors;
    long durationMaxMs;
    final long[] buckets = new long[ObservedCall.BUCKET_BOUNDS_MS.length + 1];
    OffsetDateTime lastSeen;

    Totals(String source, String target, Protocol protocol) {
      this.source = source;
      this.target = target;
      this.protocol = protocol;
    }

    void add(ObservedCall call) {
      calls += call.getCalls();
      errors += call.getErrors();
      durationMaxMs = Math.max(durationMaxMs, call.getDurationMaxMs());
      long[] b = call.buckets();
      for (int i = 0; i < b.length; i++) {
        buckets[i] += b[i];
      }
      OffsetDateTime end = call.getBucketStart().plusMinutes(1);
      if (lastSeen == null || end.isAfter(lastSeen)) {
        lastSeen = end;
      }
    }

    FlowDto.Call toDto(int minutes) {
      return new FlowDto.Call(
          source,
          target,
          protocol,
          calls,
          errors,
          Math.round(calls * 100.0 / minutes) / 100.0,
          calls > 0 ? Math.round(errors * 10000.0 / calls) / 100.0 : null,
          GraphService.percentileMs(buckets, calls, durationMaxMs, 0.5),
          GraphService.percentileMs(buckets, calls, durationMaxMs, 0.95),
          lastSeen);
    }
  }
}
