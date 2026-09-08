import { apiClient } from './client';
import type { ServiceStatus } from '@/components/status/StatusIndicator';

export interface ServiceDto {
  id: string;
  name: string;
  description: string;
  environment: string;
  repositoryUrl: string;
  healthEndpoint: string;
  status: ServiceStatus;
  statusMessage: string;
  statusUpdatedAt: string;
  createdAt: string;
}

export const ServicesApi = {
  getServices: async (orgId: string): Promise<ServiceDto[]> => {
    const { data } = await apiClient.get<ServiceDto[]>(`/organizations/${orgId}/services`);
    return data;
  },
};
