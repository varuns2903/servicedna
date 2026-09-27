package com.servicedna.graph.service;

import com.servicedna.graph.service.ObservedCallCollector.Inbound;
import com.servicedna.graph.service.ObservedCallCollector.Outbound;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where the halves of a call wait for each other. The caller's and the callee's spans arrive in
 * separate export batches — with several backend instances, possibly on different instances — so
 * pairing needs a store every instance shares ({@link RedisPendingCalls}); a single instance can
 * keep them in memory ({@link InMemoryPendingCalls}).
 */
interface PendingCalls {

  /** Pairs the caller's half with its waiting callee half (removing it), or stores it to wait. */
  Optional<Inbound> pairOutbound(String key, Outbound call);

  /** Pairs the callee's half with its waiting caller half (removing it), or stores it to wait. */
  Optional<Outbound> pairInbound(String key, Inbound call);

  /** Caller halves nothing answered in time, each handed to exactly one instance. */
  List<Outbound> takeExpired(Instant now);
}
