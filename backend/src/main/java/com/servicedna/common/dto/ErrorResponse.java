package com.servicedna.common.dto;

import java.time.OffsetDateTime;

public record ErrorResponse(
    OffsetDateTime timestamp,
    int status,
    String errorCode,
    String message,
    String path,
    String requestId) {}
