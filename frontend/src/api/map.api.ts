import { apiClient } from './client';
import type { ServiceStatus } from '@/components/status/StatusIndicator';

export interface ServiceMapNodeDto {
  id: string;
  name: string;
  status: ServiceStatus;
  latencyMs: number | null;
  environment: string;
}

export interface ServiceMapEdgeDto {
  sourceId: string;
  targetId: string;
  type: string;
}

export interface ServiceMapDto {
  nodes: ServiceMapNodeDto[];
  edges: ServiceMapEdgeDto[];
}

export const MapApi = {
  getServiceMap: async (orgId: string): Promise<ServiceMapDto> => {
    const { data } = await apiClient.get<ServiceMapDto>(`/organizations/${orgId}/services/map`);
    return data;
  },
};
