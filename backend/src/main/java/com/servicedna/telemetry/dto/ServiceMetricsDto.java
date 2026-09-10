package com.servicedna.telemetry.dto;

import java.util.List;

public record ServiceMetricsDto(
    double uptimePercentage,
    Double avgLatencyMs,
    int pingCount,
    List<MetricPointDto> dataPoints) {}
