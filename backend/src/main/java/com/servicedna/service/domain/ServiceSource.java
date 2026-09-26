package com.servicedna.service.domain;

/** How a service came to be registered. */
public enum ServiceSource {
  /** Registered by a person (UI, API, or CSV import). */
  MANUAL,
  /** Registered automatically from its own telemetry. */
  TELEMETRY
}
