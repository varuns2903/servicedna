import { apiClient } from './client';

export type IncidentStatus = 'INVESTIGATING' | 'IDENTIFIED' | 'MONITORING' | 'RESOLVED';
export type IncidentSeverity = 'SEV1' | 'SEV2' | 'SEV3';

export interface IncidentDto {
  id: string;
  organizationId: string;
  createdById: string;
  title: string;
  description: string;
  status: IncidentStatus;
  severity: IncidentSeverity;
  affectedServiceIds: string[];
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PostMortemDto {
  id: string;
  incidentId: string;
  content: string;
  authorId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateIncidentRequest {
  title: string;
  description: string;
  severity: IncidentSeverity;
  affectedServiceIds: string[];
}

export interface UpdateIncidentStatusRequest {
  status: IncidentStatus;
}

export interface UpsertPostMortemRequest {
  content: string;
}

export const IncidentsApi = {
  create: async (orgId: string, data: CreateIncidentRequest) => {
    const res = await apiClient.post<IncidentDto>(`/organizations/${orgId}/incidents`, data);
    return res.data;
  },

  getAll: async (orgId: string) => {
    const res = await apiClient.get<IncidentDto[]>(`/organizations/${orgId}/incidents`);
    return res.data;
  },

  getById: async (orgId: string, incidentId: string) => {
    const res = await apiClient.get<IncidentDto>(`/organizations/${orgId}/incidents/${incidentId}`);
    return res.data;
  },

  updateStatus: async (orgId: string, incidentId: string, data: UpdateIncidentStatusRequest) => {
    const res = await apiClient.patch<IncidentDto>(`/organizations/${orgId}/incidents/${incidentId}/status`, data);
    return res.data;
  },

  getPostMortem: async (orgId: string, incidentId: string) => {
    const res = await apiClient.get<PostMortemDto>(`/organizations/${orgId}/incidents/${incidentId}/post-mortem`);
    return res.data;
  },

  upsertPostMortem: async (orgId: string, incidentId: string, data: UpsertPostMortemRequest) => {
    const res = await apiClient.put<PostMortemDto>(`/organizations/${orgId}/incidents/${incidentId}/post-mortem`, data);
    return res.data;
  }
};
