package com.servicedna.graph.dto;

import com.servicedna.graph.domain.Protocol;
import com.servicedna.graph.domain.TargetKind;
import com.servicedna.service.domain.ServiceStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The organization's dependency graph: every registered service, plus databases, external hosts
 * and topics seen in traces. An edge is {@code declared} (entered by a person) and/or
 * {@code observed} (seen in traces within the window) — the difference is drift.
 */
public record GraphDto(OffsetDateTime since, List<Node> nodes, List<Edge> edges) {

  /** {@code id} is the service id for services, {@code KIND:name} otherwise. */
  public record Node(
      String id, String name, TargetKind kind, ServiceStatus status, String environment, String language) {}

  public record Edge(
      String source,
      String target,
      boolean declared,
      boolean observed,
      List<Protocol> protocols,
      long calls,
      long errors,
      double callsPerMinute,
      Double errorRate,
      Double avgMs,
      Long p95Ms,
      OffsetDateTime lastSeen) {}
}
