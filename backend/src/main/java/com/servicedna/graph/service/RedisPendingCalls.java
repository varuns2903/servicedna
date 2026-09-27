package com.servicedna.graph.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.servicedna.graph.domain.Protocol;
import com.servicedna.graph.service.ObservedCallCollector.Inbound;
import com.servicedna.graph.service.ObservedCallCollector.Outbound;
import io.opentelemetry.proto.common.v1.KeyValueList;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Pending calls in Redis, shared by every backend instance. Each half is a key named after the
 * caller's span; pairing is one Lua script per half — "take the other half if it's waiting, else
 * leave mine" — so two halves arriving at once on different instances can't miss each other.
 * Caller halves also sit in a sorted set by deadline; when one expires unanswered, exactly one
 * instance claims it (also atomically) and records it as a call to a database, host or topic.
 *
 * <p>All keys share the {@code {pairing}} hash tag, so the scripts work on Redis Cluster too.
 */
@Component
@ConditionalOnProperty(name = "graph.pairing-store", havingValue = "redis", matchIfMissing = true)
class RedisPendingCalls implements PendingCalls {

  private static final Logger log = LoggerFactory.getLogger(RedisPendingCalls.class);
  static final String DEFAULT_PREFIX = "sdna:{pairing}:";
  private static final int MAX_PENDING = 1_000_000;
  private static final int SWEEP_BATCH = 1000;

  // KEYS: other half, my half, deadlines. ARGV: my value, ttl ms, deadline (or "" for none), member, max pending.
  private static final RedisScript<String> PAIR = new DefaultRedisScript<>("""
      local other = redis.call('GET', KEYS[1])
      if other then
        redis.call('DEL', KEYS[1])
        redis.call('ZREM', KEYS[3], ARGV[4])
        return other
      end
      if redis.call('ZCARD', KEYS[3]) < tonumber(ARGV[5]) then
        redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2])
        if ARGV[3] ~= '' then
          redis.call('ZADD', KEYS[3], ARGV[3], ARGV[4])
        end
      end
      return false
      """, String.class);

  // KEYS: deadlines, caller half. ARGV: member. Whoever removes the member owns the expired call.
  private static final RedisScript<String> CLAIM = new DefaultRedisScript<>("""
      if redis.call('ZREM', KEYS[1], ARGV[1]) == 1 then
        local value = redis.call('GET', KEYS[2])
        redis.call('DEL', KEYS[2])
        return value
      end
      return false
      """, String.class);

  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final Duration window;
  private final String prefix;
  private final String deadlines;

  @Autowired
  RedisPendingCalls(StringRedisTemplate redis, ObjectMapper json, @Value("${graph.pairing-window-ms:30000}") long windowMs) {
    this(redis, json, Duration.ofMillis(windowMs), DEFAULT_PREFIX);
  }

  /** {@code prefix} must contain a hash tag ({…}) so a script's keys share a cluster slot. */
  RedisPendingCalls(StringRedisTemplate redis, ObjectMapper json, Duration window, String prefix) {
    this.redis = redis;
    this.json = json;
    this.window = window;
    this.prefix = prefix;
    this.deadlines = prefix + "deadlines";
  }

  /** A caller half as stored: span attributes as protobuf, times as epoch milliseconds. */
  record StoredOutbound(UUID organizationId, UUID serviceId, String environment, String sourceOperation, Protocol protocol,
      String attributes, String spanName, long startMs, long durationMs, boolean error, long expiresAtMs) {}

  record StoredInbound(UUID serviceId, String operation, boolean error, long expiresAtMs) {}

  @Override
  public Optional<Inbound> pairOutbound(String key, Outbound call) {
    // The caller half outlives its deadline by a window, so a sweep can still read it.
    String other = redis.execute(PAIR, List.of(prefix + "in:" + key, prefix + "out:" + key, deadlines),
        write(toStored(call)), String.valueOf(window.multipliedBy(2).toMillis()),
        String.valueOf(call.expiresAt().toEpochMilli()), key, String.valueOf(MAX_PENDING));
    return Optional.ofNullable(other).map(v -> fromStored(read(v, StoredInbound.class)));
  }

  @Override
  public Optional<Outbound> pairInbound(String key, Inbound call) {
    String other = redis.execute(PAIR, List.of(prefix + "out:" + key, prefix + "in:" + key, deadlines),
        write(new StoredInbound(call.serviceId(), call.operation(), call.error(), call.expiresAt().toEpochMilli())),
        String.valueOf(window.toMillis()), "", key, String.valueOf(MAX_PENDING));
    return Optional.ofNullable(other).map(v -> fromStored(read(v, StoredOutbound.class)));
  }

  @Override
  public List<Outbound> takeExpired(Instant now) {
    List<Outbound> expired = new ArrayList<>();
    Set<String> due = redis.opsForZSet().rangeByScore(deadlines, Double.NEGATIVE_INFINITY, now.toEpochMilli(), 0, SWEEP_BATCH);
    if (due == null) {
      return expired;
    }
    for (String key : due) {
      String value = redis.execute(CLAIM, List.of(deadlines, prefix + "out:" + key), key);
      if (value != null) {
        try {
          expired.add(fromStored(read(value, StoredOutbound.class)));
        } catch (RuntimeException e) {
          log.warn("Dropping an unreadable pending call: {}", e.getMessage());
        }
      }
    }
    return expired;
  }

  private StoredOutbound toStored(Outbound c) {
    String attributes = Base64.getEncoder().encodeToString(KeyValueList.newBuilder().addAllValues(c.attributes()).build().toByteArray());
    return new StoredOutbound(c.organizationId(), c.serviceId(), c.environment(), c.sourceOperation(), c.protocol(), attributes,
        c.spanName(), c.start().toEpochMilli(), c.durationMs(), c.error(), c.expiresAt().toEpochMilli());
  }

  private static Outbound fromStored(StoredOutbound s) {
    try {
      KeyValueList attributes = KeyValueList.parseFrom(Base64.getDecoder().decode(s.attributes()));
      return new Outbound(s.organizationId(), s.serviceId(), s.environment(), s.sourceOperation(), s.protocol(),
          attributes.getValuesList(), s.spanName(), Instant.ofEpochMilli(s.startMs()), s.durationMs(), s.error(),
          Instant.ofEpochMilli(s.expiresAtMs()));
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("Unreadable span attributes", e);
    }
  }

  private static Inbound fromStored(StoredInbound s) {
    return new Inbound(s.serviceId(), s.operation(), s.error(), Instant.ofEpochMilli(s.expiresAtMs()));
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private <T> T read(String value, Class<T> type) {
    try {
      return json.readValue(value, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
