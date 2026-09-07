package com.servicedna.telemetry.dto;

import java.time.OffsetDateTime;

public record PingResponse(
    boolean success,
    OffsetDateTime timestamp
) {}
