package com.servicedna.user.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DataExportDto(
    UUID userId,
    String email,
    OffsetDateTime accountCreatedAt,
    List<OrganizationMembershipExportDto> organizations,
    List<IncidentExportDto> incidentsCreated) {

  public record OrganizationMembershipExportDto(
      UUID organizationId, String organizationName, String role, OffsetDateTime memberSince) {}

  public record IncidentExportDto(
      UUID incidentId,
      String title,
      String severity,
      String status,
      OffsetDateTime createdAt) {}
}
