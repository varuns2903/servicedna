package com.servicedna.alert.domain;

public enum AlertCondition {
  STATUS_DOWN,
  STATUS_DEGRADED,
  STATUS_RECOVERED,
  /** Average latency of successful checks over {@code windowMinutes} is above {@code threshold} ms. */
  LATENCY_ABOVE,
  /** Share of failed (DOWN) checks over {@code windowMinutes} is above {@code threshold} percent. */
  ERROR_RATE_ABOVE,
  /** The last {@code threshold} checks all failed. */
  CONSECUTIVE_FAILURES,
  /** The service started calling a service it doesn't declare as a dependency (seen in traffic for the first time). */
  UNDECLARED_DEPENDENCY;

  /** Threshold conditions are evaluated on a schedule against recent pings, not on status changes. */
  public boolean isThreshold() {
    return this == LATENCY_ABOVE || this == ERROR_RATE_ABOVE || this == CONSECUTIVE_FAILURES;
  }

  public boolean usesWindow() {
    return this == LATENCY_ABOVE || this == ERROR_RATE_ABOVE;
  }
}
