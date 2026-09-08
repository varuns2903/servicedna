package com.servicedna.dashboard.dto;

import com.servicedna.service.domain.ServiceStatus;
import java.util.UUID;

public record DashboardServiceOverviewDto(
    UUID id, String name, ServiceStatus status, Double averageLatencyMs)
    implements java.io.Serializable {}
