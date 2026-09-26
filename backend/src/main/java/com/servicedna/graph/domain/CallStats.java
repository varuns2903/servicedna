package com.servicedna.graph.domain;

/** Counts accumulated in memory before being flushed into an {@link ObservedCall} row. */
public final class CallStats {

  private long calls;
  private long errors;
  private long durationSumMs;
  private long durationMaxMs;
  private final long[] buckets = new long[ObservedCall.BUCKET_BOUNDS_MS.length + 1];

  public synchronized void record(long durationMs, boolean error) {
    calls++;
    if (error) {
      errors++;
    }
    durationSumMs += durationMs;
    durationMaxMs = Math.max(durationMaxMs, durationMs);
    buckets[bucketIndex(durationMs)]++;
  }

  static int bucketIndex(long durationMs) {
    long[] bounds = ObservedCall.BUCKET_BOUNDS_MS;
    for (int i = 0; i < bounds.length; i++) {
      if (durationMs <= bounds[i]) {
        return i;
      }
    }
    return bounds.length;
  }

  public synchronized long calls() {
    return calls;
  }

  public synchronized long errors() {
    return errors;
  }

  public synchronized long durationSumMs() {
    return durationSumMs;
  }

  public synchronized long durationMaxMs() {
    return durationMaxMs;
  }

  public synchronized long[] buckets() {
    return buckets.clone();
  }
}
