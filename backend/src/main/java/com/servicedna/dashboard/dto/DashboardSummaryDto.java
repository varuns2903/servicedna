package com.servicedna.dashboard.dto;

import com.servicedna.incident.dto.IncidentDto;

import java.util.List;

public record DashboardSummaryDto(
    int totalServices,
    int healthyServices,
    int activeIncidentsCount,
    List<IncidentDto> activeIncidents,
    List<DashboardServiceOverviewDto> servicesOverview
) {}
