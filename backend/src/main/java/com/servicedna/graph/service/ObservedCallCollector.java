package com.servicedna.graph.service;

import com.google.protobuf.ByteString;
import com.servicedna.graph.domain.CallKey;
import com.servicedna.graph.domain.Protocol;
import com.servicedna.graph.domain.SpanAttributes;
import com.servicedna.graph.domain.TargetKind;
import com.servicedna.ingestion.event.TraceBatchReceivedEvent;
import com.servicedna.ingestion.service.ServiceDiscoveryListener;
import com.servicedna.service.dto.TelemetryIdentity;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Turns traces into observed calls. A call is an outgoing span (CLIENT or PRODUCER) in one
 * service paired with the incoming span (SERVER or CONSUMER) it caused in another — the incoming
 * span's parent (or, for messaging, a link) is the outgoing span. Services export separately, so
 * the halves arrive in different batches; each waits up to {@code graph.pairing-window-ms} for the
 * other. An outgoing span nobody answers becomes a call to a database, external host or topic,
 * when its attributes say which.
 *
 * <p>State is in memory: with several backend instances, the halves of a call can land on
 * different instances and go unpaired.
 */
@Component
public class ObservedCallCollector {

  private static final Logger log = LoggerFactory.getLogger(ObservedCallCollector.class);
  private static final int MAX_PENDING = 200_000;
  private static final HexFormat HEX = HexFormat.of();

  /** The caller's half of a call. */
  record Outbound(
      UUID organizationId,
      UUID serviceId,
      String environment,
      String sourceOperation,
      Protocol protocol,
      List<KeyValue> attributes,
      String spanName,
      Instant start,
      long durationMs,
      boolean error,
      Instant expiresAt) {}

  /** The callee's half of a call. */
  record Inbound(UUID serviceId, String operation, boolean error, Instant expiresAt) {}

  private final ServiceDiscoveryListener serviceDiscovery;
  private final CallAggregator aggregator;
  private final HostServiceResolver serviceResolver;
  private final Duration pairingWindow;
  private final Map<String, Outbound> waitingOutbound = new ConcurrentHashMap<>();
  private final Map<String, Inbound> waitingInbound = new ConcurrentHashMap<>();

  public ObservedCallCollector(
      ServiceDiscoveryListener serviceDiscovery,
      CallAggregator aggregator,
      HostServiceResolver serviceResolver,
      @Value("${graph.pairing-window-ms:30000}") long pairingWindowMs) {
    this.serviceDiscovery = serviceDiscovery;
    this.aggregator = aggregator;
    this.serviceResolver = serviceResolver;
    this.pairingWindow = Duration.ofMillis(pairingWindowMs);
  }

  @EventListener
  public void onTraceBatch(TraceBatchReceivedEvent event) {
    try {
      for (ResourceSpans resourceSpans : event.request().getResourceSpansList()) {
        collect(event.organizationId(), resourceSpans);
      }
    } catch (RuntimeException e) {
      // Best-effort: failing here would fail the export and make the client resend stored spans.
      log.warn("Collecting observed calls failed: {}", e.getMessage());
    }
  }

  void collect(UUID organizationId, ResourceSpans resourceSpans) {
    TelemetryIdentity identity =
        ServiceDiscoveryListener.identityOf(resourceSpans.getResource().getAttributesList());
    if (identity == null) {
      return;
    }
    // Also what registers a new service (cached for known ones), so listener order doesn't matter.
    Optional<UUID> serviceId = serviceDiscovery.register(organizationId, identity);
    if (serviceId.isEmpty()) {
      return;
    }

    Map<ByteString, Span> byId = new HashMap<>();
    for (ScopeSpans scope : resourceSpans.getScopeSpansList()) {
      for (Span span : scope.getSpansList()) {
        byId.put(span.getSpanId(), span);
      }
    }
    Instant now = Instant.now();
    for (Span span : byId.values()) {
      switch (span.getKind()) {
        case SPAN_KIND_CLIENT, SPAN_KIND_PRODUCER -> outbound(organizationId, serviceId.get(), identity.environment(), span, byId, now);
        case SPAN_KIND_SERVER, SPAN_KIND_CONSUMER -> inbound(organizationId, serviceId.get(), span, now);
        default -> {}
      }
    }
  }

  private void outbound(UUID organizationId, UUID serviceId, String environment, Span span, Map<ByteString, Span> byId, Instant now) {
    List<KeyValue> attributes = span.getAttributesList();
    Protocol protocol = Protocol.of(attributes);
    Outbound call =
        new Outbound(
            organizationId,
            serviceId,
            environment,
            entryOperation(span, byId),
            protocol,
            attributes,
            span.getName(),
            Instant.ofEpochSecond(0, span.getStartTimeUnixNano()),
            Math.max(0, (span.getEndTimeUnixNano() - span.getStartTimeUnixNano()) / 1_000_000),
            span.getStatus().getCode() == Status.StatusCode.STATUS_CODE_ERROR,
            now.plus(pairingWindow));

    // Databases are never instrumented on the other side; record them straight away.
    if (protocol == Protocol.DATABASE) {
      recordUnanswered(call);
      return;
    }
    String key = key(organizationId, span.getTraceId(), span.getSpanId());
    Inbound answer = waitingInbound.remove(key);
    if (answer != null) {
      recordPaired(call, answer);
    } else if (waitingOutbound.size() < MAX_PENDING) {
      waitingOutbound.put(key, call);
    }
  }

  private void inbound(UUID organizationId, UUID serviceId, Span span, Instant now) {
    Inbound call =
        new Inbound(
            serviceId,
            operationName(span),
            span.getStatus().getCode() == Status.StatusCode.STATUS_CODE_ERROR,
            now.plus(pairingWindow));
    // The caller is the parent; a messaging consumer may instead link to the producer.
    if (!span.getParentSpanId().isEmpty()) {
      match(key(organizationId, span.getTraceId(), span.getParentSpanId()), call);
    }
    for (Span.Link link : span.getLinksList()) {
      match(key(organizationId, link.getTraceId(), link.getSpanId()), call);
    }
  }

  private void match(String key, Inbound call) {
    Outbound caller = waitingOutbound.remove(key);
    if (caller != null) {
      recordPaired(caller, call);
    } else if (waitingInbound.size() < MAX_PENDING) {
      waitingInbound.put(key, call);
    }
  }

  private void recordPaired(Outbound caller, Inbound callee) {
    aggregator.record(
        caller.organizationId(),
        caller.start(),
        new CallKey(
            caller.serviceId(),
            caller.sourceOperation(),
            callee.serviceId(),
            TargetKind.SERVICE,
            callee.serviceId().toString(),
            callee.operation(),
            caller.protocol()),
        caller.durationMs(),
        caller.error() || callee.error());
  }

  /** A call nothing instrumented answered: record it against its database, host or topic. */
  private void recordUnanswered(Outbound call) {
    List<KeyValue> a = call.attributes();
    TargetKind kind;
    String name;
    String operation;
    switch (call.protocol()) {
      case DATABASE -> {
        kind = TargetKind.DATABASE;
        String system = SpanAttributes.string(a, "db.system.name", "db.system");
        String namespace = SpanAttributes.string(a, "db.namespace", "db.name");
        name = namespace != null ? system + "/" + namespace : system;
        operation = firstNonNull(SpanAttributes.string(a, "db.operation.name", "db.operation"), firstWord(SpanAttributes.string(a, "db.query.text", "db.statement")), call.spanName());
      }
      case MESSAGING -> {
        kind = TargetKind.TOPIC;
        name = SpanAttributes.string(a, "messaging.destination.name", "messaging.destination");
        operation = "publish";
      }
      default -> {
        String host = firstNonNull(SpanAttributes.string(a, "peer.service", "server.address", "net.peer.name"), host(SpanAttributes.string(a, "url.full", "http.url")));
        String method = SpanAttributes.string(a, "http.request.method", "http.method");
        String path = normalizePath(firstNonNull(SpanAttributes.string(a, "url.path"), path(SpanAttributes.string(a, "url.full", "http.url"))));
        // Docker and Kubernetes hostnames are usually service names: a call to "product-service"
        // whose server side didn't trace it (health checks, sampling) is still a call to that service.
        Optional<UUID> service = host != null ? serviceResolver.byHostname(call.organizationId(), host, call.environment()) : Optional.empty();
        if (service.isPresent()) {
          aggregator.record(
              call.organizationId(),
              call.start(),
              new CallKey(call.serviceId(), call.sourceOperation(), service.get(), TargetKind.SERVICE, service.get().toString(),
                  truncate(method != null && path != null ? method + " " + path : orEmpty(method)), call.protocol()),
              call.durationMs(),
              call.error());
          return;
        }
        kind = TargetKind.EXTERNAL;
        name = host;
        operation = method != null ? method : call.spanName();
      }
    }
    if (name == null) {
      return;
    }
    aggregator.record(
        call.organizationId(),
        call.start(),
        new CallKey(call.serviceId(), call.sourceOperation(), null, kind, truncate(name), truncate(orEmpty(operation)), call.protocol()),
        call.durationMs(),
        call.error());
  }

  @Scheduled(fixedDelayString = "${graph.pairing-sweep-ms:5000}")
  public void expireUnpaired() {
    Instant now = Instant.now();
    for (Iterator<Map.Entry<String, Outbound>> it = waitingOutbound.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, Outbound> entry = it.next();
      if (entry.getValue().expiresAt().isBefore(now) && waitingOutbound.remove(entry.getKey(), entry.getValue())) {
        recordUnanswered(entry.getValue());
      }
    }
    // An incoming span whose caller never reported (an uninstrumented client) has no edge to add.
    waitingInbound.values().removeIf(call -> call.expiresAt().isBefore(now));
  }

  /** The incoming operation (in the same service) that led to this outgoing span, if exported with it. */
  static String entryOperation(Span span, Map<ByteString, Span> byId) {
    ByteString parent = span.getParentSpanId();
    for (int depth = 0; depth < 64 && !parent.isEmpty(); depth++) {
      Span ancestor = byId.get(parent);
      if (ancestor == null) {
        return "";
      }
      if (ancestor.getKind() == Span.SpanKind.SPAN_KIND_SERVER || ancestor.getKind() == Span.SpanKind.SPAN_KIND_CONSUMER) {
        return operationName(ancestor);
      }
      if (ancestor.getParentSpanId().isEmpty()) {
        return truncate(ancestor.getName()); // a root that isn't a request: a job, a startup task
      }
      parent = ancestor.getParentSpanId();
    }
    return "";
  }

  /** "METHOD /route" for HTTP, "service/method" for RPC, the span name otherwise. */
  static String operationName(Span span) {
    List<KeyValue> a = span.getAttributesList();
    String route = SpanAttributes.string(a, "http.route");
    if (route != null) {
      String method = SpanAttributes.string(a, "http.request.method", "http.method");
      return truncate(method != null ? method + " " + route : route);
    }
    String rpcService = SpanAttributes.string(a, "rpc.service");
    String rpcMethod = SpanAttributes.string(a, "rpc.method");
    if (rpcService != null && rpcMethod != null) {
      return truncate(rpcService + "/" + rpcMethod);
    }
    if (span.getKind() == Span.SpanKind.SPAN_KIND_CONSUMER) {
      String destination = SpanAttributes.string(a, "messaging.destination.name", "messaging.destination");
      if (destination != null) {
        return truncate("consume " + destination);
      }
    }
    return truncate(span.getName());
  }

  private static String key(UUID organizationId, ByteString traceId, ByteString spanId) {
    return organizationId + "|" + HEX.formatHex(traceId.toByteArray()) + "|" + HEX.formatHex(spanId.toByteArray());
  }

  private static String host(String url) {
    if (url == null) {
      return null;
    }
    try {
      return URI.create(url).getHost();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String path(String url) {
    if (url == null) {
      return null;
    }
    try {
      return URI.create(url).getPath();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** Replaces id-like segments ("/users/u-17", "/orders/42") with {id} to keep operations few. */
  public static String normalizePath(String path) {
    if (path == null || path.isEmpty()) {
      return path;
    }
    String[] segments = path.split("/", -1);
    for (int i = 0; i < segments.length; i++) {
      if (segments[i].chars().anyMatch(Character::isDigit)) {
        segments[i] = "{id}";
      }
    }
    return String.join("/", segments);
  }

  private static String firstWord(String statement) {
    if (statement == null) {
      return null;
    }
    String trimmed = statement.trim();
    int space = trimmed.indexOf(' ');
    return (space < 0 ? trimmed : trimmed.substring(0, space)).toUpperCase();
  }

  @SafeVarargs
  private static <T> T firstNonNull(T... values) {
    for (T value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String truncate(String value) {
    return value.length() <= 255 ? value : value.substring(0, 255);
  }
}
