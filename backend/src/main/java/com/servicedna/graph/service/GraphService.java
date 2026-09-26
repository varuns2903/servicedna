package com.servicedna.graph.service;

import com.servicedna.graph.domain.ObservedCall;
import com.servicedna.graph.domain.Protocol;
import com.servicedna.graph.domain.TargetKind;
import com.servicedna.graph.dto.GraphDto;
import com.servicedna.graph.repository.ObservedCallRepository;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class GraphService {

  public static final int MAX_WINDOW_MINUTES = 7 * 24 * 60;

  private final ObservedCallRepository observedCallRepository;
  private final ServiceRepository serviceRepository;
  private final OrganizationService organizationService;

  public GraphService(
      ObservedCallRepository observedCallRepository,
      ServiceRepository serviceRepository,
      OrganizationService organizationService) {
    this.observedCallRepository = observedCallRepository;
    this.serviceRepository = serviceRepository;
    this.organizationService = organizationService;
  }

  @Transactional(readOnly = true)
  public GraphDto graph(UUID organizationId, int windowMinutes, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    int window = Math.max(1, Math.min(windowMinutes, MAX_WINDOW_MINUTES));
    OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES).minusMinutes(window);

    List<Service> services = serviceRepository.findByOrganizationId(organizationId);
    Map<String, GraphDto.Node> nodes = new LinkedHashMap<>();
    for (Service s : services) {
      nodes.put(
          s.getId().toString(),
          new GraphDto.Node(s.getId().toString(), s.getName(), TargetKind.SERVICE, s.getStatus(), s.getEnvironment(), s.getLanguage()));
    }

    Map<String, EdgeTotals> edges = new LinkedHashMap<>();
    for (Service s : services) {
      for (Service dependency : s.getDependencies()) {
        edges.computeIfAbsent(edgeKey(s.getId().toString(), dependency.getId().toString()), k -> new EdgeTotals(s.getId().toString(), dependency.getId().toString())).declared = true;
      }
    }
    for (ObservedCall call : observedCallRepository.findByOrganizationIdAndBucketStartGreaterThanEqual(organizationId, since)) {
      String target = targetId(call);
      if (call.getTargetKind() != TargetKind.SERVICE) {
        nodes.putIfAbsent(target, new GraphDto.Node(target, call.getTargetName(), call.getTargetKind(), null, null, null));
      }
      String source = call.getSourceServiceId().toString();
      edges.computeIfAbsent(edgeKey(source, target), k -> new EdgeTotals(source, target)).add(call);
    }

    List<GraphDto.Edge> result = new ArrayList<>();
    for (EdgeTotals e : edges.values()) {
      if (nodes.containsKey(e.source) && nodes.containsKey(e.target)) {
        result.add(e.toDto(window));
      }
    }
    return new GraphDto(since, new ArrayList<>(nodes.values()), result);
  }

  static String targetId(ObservedCall call) {
    return call.getTargetKind() == TargetKind.SERVICE
        ? call.getTargetServiceId().toString()
        : call.getTargetKind() + ":" + call.getTargetName();
  }

  private static String edgeKey(String source, String target) {
    return source + "->" + target;
  }

  /** Approximate percentile from the latency histogram: the upper bound of the bucket it falls in. */
  public static Long percentileMs(long[] buckets, long total, long maxMs, double percentile) {
    if (total == 0) {
      return null;
    }
    long threshold = (long) Math.ceil(total * percentile);
    long cumulative = 0;
    for (int i = 0; i < buckets.length; i++) {
      cumulative += buckets[i];
      if (cumulative >= threshold) {
        return i < ObservedCall.BUCKET_BOUNDS_MS.length ? Math.min(ObservedCall.BUCKET_BOUNDS_MS[i], maxMs) : maxMs;
      }
    }
    return maxMs;
  }

  private static final class EdgeTotals {
    final String source;
    final String target;
    boolean declared;
    long calls;
    long errors;
    long durationSumMs;
    long durationMaxMs;
    final long[] buckets = new long[ObservedCall.BUCKET_BOUNDS_MS.length + 1];
    final Set<Protocol> protocols = EnumSet.noneOf(Protocol.class);
    OffsetDateTime lastSeen;

    EdgeTotals(String source, String target) {
      this.source = source;
      this.target = target;
    }

    void add(ObservedCall call) {
      calls += call.getCalls();
      errors += call.getErrors();
      durationSumMs += call.getDurationSumMs();
      durationMaxMs = Math.max(durationMaxMs, call.getDurationMaxMs());
      long[] b = call.buckets();
      for (int i = 0; i < b.length; i++) {
        buckets[i] += b[i];
      }
      protocols.add(call.getProtocol());
      OffsetDateTime end = call.getBucketStart().plusMinutes(1);
      if (lastSeen == null || end.isAfter(lastSeen)) {
        lastSeen = end;
      }
    }

    GraphDto.Edge toDto(int windowMinutes) {
      boolean observed = calls > 0;
      return new GraphDto.Edge(
          source,
          target,
          declared,
          observed,
          List.copyOf(protocols),
          calls,
          errors,
          observed ? Math.round(calls * 100.0 / windowMinutes) / 100.0 : 0,
          observed ? Math.round(errors * 10000.0 / calls) / 100.0 : null,
          observed ? Math.round(durationSumMs * 10.0 / calls) / 10.0 : null,
          percentileMs(buckets, calls, durationMaxMs, 0.95),
          lastSeen);
    }
  }
}
