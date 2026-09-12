import { apiClient } from './client';
import type { ServiceStatus } from '@/components/status/StatusIndicator';

export interface ServiceDto {
  id: string;
  organizationId: string;
  name: string;
  description: string;
  repositoryUrl: string;
  region: string;
  healthCheckUrl: string | null;
  sloTargetPercentage: number;
  status: ServiceStatus;
  // Only populated in the response to createService — redacted (null) on every list/get call
  // so the credential isn't re-exposed to every org member on every read.
  apiKey: string | null;
  dependencyIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateServiceRequest {
  name: string;
  description?: string;
  repositoryUrl?: string;
  region?: string;
  healthCheckUrl?: string;
}

export interface UpdateServiceRequest {
  name: string;
  description?: string;
  repositoryUrl?: string;
  region?: string;
  healthCheckUrl?: string;
  sloTargetPercentage?: number;
}

export type MetricsRange = '24h' | '7d' | '30d';

export interface MetricPointDto {
  timestamp: string;
  status: ServiceStatus;
  latencyMs: number | null;
}

export interface ServiceMetricsDto {
  uptimePercentage: number;
  avgLatencyMs: number | null;
  pingCount: number;
  dataPoints: MetricPointDto[];
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

  createService: async (
    orgId: string,
    request: CreateServiceRequest
  ): Promise<ServiceDto & { apiKey: string }> => {
    const { data } = await apiClient.post<ServiceDto & { apiKey: string }>(
      `/organizations/${orgId}/services`,
      request
    );
    return data;
  },

  addDependency: async (orgId: string, serviceId: string, dependsOnServiceId: string): Promise<ServiceDto> => {
    const { data } = await apiClient.post<ServiceDto>(
      `/organizations/${orgId}/services/${serviceId}/dependencies`,
      { dependsOnServiceId }
    );
    return data;
  },

  updateService: async (
    orgId: string,
    serviceId: string,
    request: UpdateServiceRequest
  ): Promise<ServiceDto> => {
    const { data } = await apiClient.put<ServiceDto>(
      `/organizations/${orgId}/services/${serviceId}`,
      request
    );
    return data;
  },

  deleteService: async (orgId: string, serviceId: string): Promise<void> => {
    await apiClient.delete(`/organizations/${orgId}/services/${serviceId}`);
  },

  regenerateApiKey: async (
    orgId: string,
    serviceId: string
  ): Promise<ServiceDto & { apiKey: string }> => {
    const { data } = await apiClient.post<ServiceDto & { apiKey: string }>(
      `/organizations/${orgId}/services/${serviceId}/api-key/regenerate`
    );
    return data;
  },

  getServiceMetrics: async (
    orgId: string,
    serviceId: string,
    range: MetricsRange
  ): Promise<ServiceMetricsDto> => {
    const { data } = await apiClient.get<ServiceMetricsDto>(
      `/organizations/${orgId}/services/${serviceId}/metrics`,
      { params: { range } }
    );
    return data;
  },
};
