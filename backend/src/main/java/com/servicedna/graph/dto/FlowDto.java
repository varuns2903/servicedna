package com.servicedna.graph.dto;

import com.servicedna.graph.domain.Protocol;
import com.servicedna.graph.domain.TargetKind;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The API flow graph: operations as nodes (an operation of a service, or of a database, host or
 * topic) and calls between them as edges. Scoped to one entry operation, it's everything that
 * operation triggers downstream.
 */
public record FlowDto(OffsetDateTime since, Entry entry, List<Operation> operations, List<Call> calls) {

  public record Entry(String nodeId, String operation) {}

  /** {@code id} is "nodeId|operation"; {@code nodeId} matches the dependency graph's node ids. */
  public record Operation(String id, String nodeId, String nodeName, TargetKind kind, String operation, long callsIn) {}

  public record Call(
      String source,
      String target,
      Protocol protocol,
      long calls,
      long errors,
      double callsPerMinute,
      Double errorRate,
      Long p50Ms,
      Long p95Ms,
      OffsetDateTime lastSeen) {}

  /** An operation callers enter through: called by nobody instrumented, or called from outside. */
  public record EntryPoint(String nodeId, String nodeName, String operation, long calls) {}
}
