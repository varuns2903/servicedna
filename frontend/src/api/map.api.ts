import { apiClient } from './client';
import type { ServiceStatus } from '@/components/status/StatusIndicator';

export interface ServiceMapNodeDto {
  id: string;
  name: string;
  region: string;
  status: ServiceStatus;
}

export interface ServiceMapEdgeDto {
  sourceId: string;
  targetId: string;
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
