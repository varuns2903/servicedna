import { apiClient } from './client';
import type { ServiceStatus } from '@/components/status/StatusIndicator';

export interface ServiceDto {
  id: string;
  organizationId: string;
  name: string;
  description: string;
  repositoryUrl: string;
  region: string;
  status: ServiceStatus;
  apiKey: string;
  dependencyIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateServiceRequest {
  name: string;
  description?: string;
  repositoryUrl?: string;
  region?: string;
}

export const ServicesApi = {
  getServices: async (orgId: string): Promise<ServiceDto[]> => {
    const { data } = await apiClient.get<ServiceDto[]>(`/organizations/${orgId}/services`);
    return data;
  },

  getService: async (orgId: string, serviceId: string): Promise<ServiceDto> => {
    const { data } = await apiClient.get<ServiceDto>(`/organizations/${orgId}/services/${serviceId}`);
    return data;
  },

  createService: async (orgId: string, request: CreateServiceRequest): Promise<ServiceDto> => {
    const { data } = await apiClient.post<ServiceDto>(`/organizations/${orgId}/services`, request);
    return data;
  },

  addDependency: async (orgId: string, serviceId: string, dependsOnServiceId: string): Promise<ServiceDto> => {
    const { data } = await apiClient.post<ServiceDto>(
      `/organizations/${orgId}/services/${serviceId}/dependencies`,
      { dependsOnServiceId }
    );
    return data;
  },
};
