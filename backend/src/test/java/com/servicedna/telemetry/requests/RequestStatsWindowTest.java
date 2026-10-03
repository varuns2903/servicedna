package com.servicedna.telemetry.requests;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestStatsWindowTest {

  // Buckets: ≤10, ≤25, ≤50, ≤100, ≤250, ≤500, ≤1000, ≤2500, ≤5000, ≤10000, more
  private static RequestStats.Window window(long errors, long maxMs, long... buckets) {
    long total = 0;
    for (long b : buckets) {
      total += b;
    }
    return new RequestStats.Window(total, errors, maxMs, buckets);
  }

  @Test
  void percentilesInterpolateWithinTheirBucket() {
    // 90 requests ≤10 ms, 10 between 250 and 500 ms: p95 is halfway through that bucket.
    RequestStats.Window w = window(0, 480, 90, 0, 0, 0, 0, 10, 0, 0, 0, 0, 0);
    assertThat(w.percentileMs(95)).isEqualTo(375.0);
    assertThat(w.percentileMs(50)).isBetween(5.0, 6.0);
  }

  @Test
  void theOpenEndedBucketUsesTheSlowestRequest() {
    RequestStats.Window w = window(0, 30_000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
    assertThat(w.percentileMs(95)).isEqualTo(30_000.0);
  }

  @Test
  void errorRateIsAPercentageOfRequests() {
    assertThat(window(3, 10, 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0).errorRatePercent()).isEqualTo(25.0);
    assertThat(window(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0).errorRatePercent()).isZero();
  }
}
