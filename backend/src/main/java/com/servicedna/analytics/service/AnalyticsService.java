package com.servicedna.analytics.service;

import com.servicedna.analytics.dto.ServiceSlaDto;
import com.servicedna.analytics.dto.SlaReportDto;
import com.servicedna.incident.domain.Incident;
import com.servicedna.incident.repository.IncidentRepository;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
public class AnalyticsService {

    private final IncidentRepository incidentRepository;
    private final ServiceRepository serviceRepository;
    private final OrganizationService organizationService;

    public AnalyticsService(
            IncidentRepository incidentRepository,
            ServiceRepository serviceRepository,
            OrganizationService organizationService
    ) {
        this.incidentRepository = incidentRepository;
        this.serviceRepository = serviceRepository;
        this.organizationService = organizationService;
    }

    @Transactional(readOnly = true)
    public SlaReportDto generateSlaReport(UUID organizationId, UUID userId, int days) {
        organizationService.validateUserAccess(organizationId, userId);

        OffsetDateTime periodEnd = OffsetDateTime.now();
        OffsetDateTime periodStart = periodEnd.minusDays(days);
        
        List<Service> services = serviceRepository.findByOrganizationId(organizationId);
        List<Incident> recentIncidents = incidentRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .filter(i -> i.getCreatedAt().isAfter(periodStart))
                .toList();

        List<ServiceSlaDto> serviceSlas = new ArrayList<>();
        
        long totalPeriodMinutes = Duration.between(periodStart, periodEnd).toMinutes();
        long orgTotalDowntimeMinutes = 0;
        long orgTotalMttrMinutes = 0;
        int orgResolvedIncidents = 0;

        for (Service service : services) {
            List<Incident> serviceIncidents = recentIncidents.stream()
                    .filter(i -> i.getAffectedServices().contains(service))
                    .toList();

            long serviceDowntimeMinutes = 0;
            long serviceMttrSum = 0;
            int serviceResolved = 0;

            for (Incident incident : serviceIncidents) {
                OffsetDateTime end = incident.getResolvedAt() != null ? incident.getResolvedAt() : periodEnd;
                long downtime = Duration.between(incident.getCreatedAt(), end).toMinutes();
                serviceDowntimeMinutes += downtime;

                if (incident.getResolvedAt() != null) {
                    serviceMttrSum += downtime;
                    serviceResolved++;
                }
            }

            double uptimePercentage = 100.0;
            if (totalPeriodMinutes > 0) {
                uptimePercentage = Math.max(0, 100.0 * (totalPeriodMinutes - serviceDowntimeMinutes) / totalPeriodMinutes);
            }

            double mttrMinutes = serviceResolved > 0 ? (double) serviceMttrSum / serviceResolved : 0.0;
            
            // MTBF = (Total Uptime) / number of incidents
            long uptimeMinutes = totalPeriodMinutes - serviceDowntimeMinutes;
            double mtbfHours = serviceIncidents.size() > 0 ? (double) uptimeMinutes / 60.0 / serviceIncidents.size() : (double) totalPeriodMinutes / 60.0;

            serviceSlas.add(new ServiceSlaDto(
                    service.getId(),
                    service.getName(),
                    uptimePercentage,
                    serviceIncidents.size(),
                    mttrMinutes,
                    mtbfHours
            ));

            orgTotalDowntimeMinutes += serviceDowntimeMinutes;
            orgTotalMttrMinutes += serviceMttrSum;
            orgResolvedIncidents += serviceResolved;
        }

        double overallUptimePercentage = 100.0;
        if (totalPeriodMinutes > 0 && !services.isEmpty()) {
            long maxPossibleDowntime = totalPeriodMinutes * services.size();
            overallUptimePercentage = Math.max(0, 100.0 * (maxPossibleDowntime - orgTotalDowntimeMinutes) / maxPossibleDowntime);
        }

        double orgMttrMinutes = orgResolvedIncidents > 0 ? (double) orgTotalMttrMinutes / orgResolvedIncidents : 0.0;
        long orgUptimeMinutes = (totalPeriodMinutes * services.size()) - orgTotalDowntimeMinutes;
        double orgMtbfHours = recentIncidents.size() > 0 ? (double) orgUptimeMinutes / 60.0 / recentIncidents.size() : (double) (totalPeriodMinutes * services.size()) / 60.0;

        return new SlaReportDto(
                organizationId,
                periodStart,
                periodEnd,
                overallUptimePercentage,
                recentIncidents.size(),
                orgMttrMinutes,
                orgMtbfHours,
                serviceSlas
        );
    }
}
