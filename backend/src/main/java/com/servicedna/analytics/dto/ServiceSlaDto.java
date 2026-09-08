package com.servicedna.analytics.dto;

import java.util.UUID;

public record ServiceSlaDto(
    UUID serviceId,
    String serviceName,
    double uptimePercentage,
    int incidentCount,
    double mttrMinutes,
    double mtbfHours)
    implements java.io.Serializable {}
