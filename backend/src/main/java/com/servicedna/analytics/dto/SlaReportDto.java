package com.servicedna.analytics.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SlaReportDto( 
    UUID organizationId,
    OffsetDateTime periodStart,
    OffsetDateTime periodEnd,
    double overallUptimePercentage,
    int totalIncidents,
    double mttrMinutes,
    double mtbfHours,
    List<ServiceSlaDto> serviceSlas
) implements java.io.Serializable {}
