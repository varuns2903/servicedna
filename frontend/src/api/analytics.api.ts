import { apiClient } from './client';

export interface ServiceSlaDto {
  serviceId: string;
  serviceName: string;
  uptimePercentage: number;
  incidentCount: number;
  mttrMinutes: number;
  mtbfHours: number;
  sloTargetPercentage: number;
  errorBudgetMinutesTotal: number;
  errorBudgetMinutesConsumed: number;
  errorBudgetRemainingPercentage: number;
}

export interface SlaReportDto {
  organizationId: string;
  periodStart: string;
  periodEnd: string;
  overallUptimePercentage: number;
  totalIncidents: number;
  mttrMinutes: number;
  mtbfHours: number;
  serviceSlas: ServiceSlaDto[];
}

export const AnalyticsApi = {
  getSlaReport: async (orgId: string, days: number): Promise<SlaReportDto> => {
    const { data } = await apiClient.get<SlaReportDto>(`/organizations/${orgId}/analytics/sla`, {
      params: { days },
    });
    return data;
  },
};
