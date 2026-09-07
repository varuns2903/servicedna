package com.servicedna.dashboard.dto;

import com.servicedna.incident.dto.IncidentDto;

import java.util.List;

import java.io.Serializable;

public record DashboardSummaryDto(
    int totalServices,
    int healthyServices,
    int activeIncidentsCount,
    List<IncidentDto> activeIncidents,
    List<DashboardServiceOverviewDto> servicesOverview
) implements java.io.Serializable {}
