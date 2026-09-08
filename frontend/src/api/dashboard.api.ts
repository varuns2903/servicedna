import { apiClient } from './client';
import type { ServiceStatus } from '@/components/status/StatusIndicator';

export interface DashboardServiceOverviewDto {
  id: string;
  name: string;
  status: ServiceStatus;
  latencyMs: number | null;
  availabilityPercentage: number | null;
}

export interface IncidentDto {
  id: string;
  title: string;
  status: string;
  severity: string;
  createdAt: string;
}

export interface DashboardSummaryDto {
  totalServices: number;
  healthyServices: number;
  degradedServices: number;
  downServices: number;
  activeIncidents: IncidentDto[];
  servicesOverview: DashboardServiceOverviewDto[];
}

export const DashboardApi = {
  getSummary: async (orgId: string): Promise<DashboardSummaryDto> => {
    const { data } = await apiClient.get<DashboardSummaryDto>(`/organizations/${orgId}/dashboard`);
    return data;
  },
};
