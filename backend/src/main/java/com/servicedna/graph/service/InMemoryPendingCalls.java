package com.servicedna.graph.service;

import com.servicedna.graph.service.ObservedCallCollector.Inbound;
import com.servicedna.graph.service.ObservedCallCollector.Outbound;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Pending calls in this instance's memory: only right with a single backend instance. */
@Component
@ConditionalOnProperty(name = "graph.pairing-store", havingValue = "memory")
class InMemoryPendingCalls implements PendingCalls {

  private static final int MAX_PENDING = 200_000;

  private final Map<String, Outbound> waitingOutbound = new ConcurrentHashMap<>();
  private final Map<String, Inbound> waitingInbound = new ConcurrentHashMap<>();

  @Override
  public Optional<Inbound> pairOutbound(String key, Outbound call) {
    Inbound answer = waitingInbound.remove(key);
    if (answer == null && waitingOutbound.size() < MAX_PENDING) {
      waitingOutbound.put(key, call);
    }
    return Optional.ofNullable(answer);
  }

  @Override
  public Optional<Outbound> pairInbound(String key, Inbound call) {
    Outbound caller = waitingOutbound.remove(key);
    if (caller == null && waitingInbound.size() < MAX_PENDING) {
      waitingInbound.put(key, call);
    }
    return Optional.ofNullable(caller);
  }

  @Override
  public List<Outbound> takeExpired(Instant now) {
    List<Outbound> expired = new ArrayList<>();
    for (Iterator<Map.Entry<String, Outbound>> it = waitingOutbound.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, Outbound> entry = it.next();
      if (entry.getValue().expiresAt().isBefore(now) && waitingOutbound.remove(entry.getKey(), entry.getValue())) {
        expired.add(entry.getValue());
      }
    }
    // An incoming span whose caller never reported (an uninstrumented client) has no edge to add.
    waitingInbound.values().removeIf(call -> call.expiresAt().isBefore(now));
    return expired;
  }
}
