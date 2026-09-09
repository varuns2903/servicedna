import { apiClient } from './client';
import type { ServiceStatus } from '@/components/status/StatusIndicator';
import type { IncidentStatus, IncidentSeverity } from './incidents.api';

export interface PublicServiceDto {
  id: string;
  name: string;
  description: string;
  status: ServiceStatus;
}

export interface PublicIncidentDto {
  id: string;
  title: string;
  description: string;
  status: IncidentStatus;
  severity: IncidentSeverity;
  createdAt: string;
  resolvedAt: string | null;
}

export interface PublicStatusPageDto {
  organizationId: string;
  organizationName: string;
  overallState: 'OPERATIONAL' | 'DEGRADED' | 'OUTAGE';
  services: PublicServiceDto[];
  activeIncidents: PublicIncidentDto[];
}

export const PublicApi = {
  getStatusPage: async (orgId: string): Promise<PublicStatusPageDto> => {
    // We can use apiClient, but we don't strictly need auth for this.
    // However, apiClient is already configured with the base URL.
    const { data } = await apiClient.get<PublicStatusPageDto>(`/public/organizations/${orgId}/status`);
    return data;
  }
};
