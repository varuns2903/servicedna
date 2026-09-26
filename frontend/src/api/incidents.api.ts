import type { TraceExplore } from './traces.api';
import type { Hop } from './testing.api';
import { apiClient } from './client';

export type IncidentStatus = 'INVESTIGATING' | 'IDENTIFIED' | 'MONITORING' | 'RESOLVED';
export type IncidentSeverity = 'CRITICAL' | 'MAJOR' | 'MINOR' | 'LOW';

export interface IncidentDto {
  id: string;
  organizationId: string;
  /** Null when an alert rule opened the incident. */
  createdById: string | null;
  /** The service whose alert opened this incident; null for incidents reported by a person. */
  triggeredByServiceId: string | null;
  title: string;
  description: string;
  status: IncidentStatus;
  severity: IncidentSeverity;
  affectedServiceIds: string[];
  resolvedAt: string | null;
  acknowledgedAt: string | null;
  escalatedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PostMortemDto {
  id: string;
  incidentId: string;
  rootCause: string;
  timeline: string;
  actionItems: string;
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
  rootCause: string;
  timeline: string;
  actionItems: string;
}

export type IncidentEventType =
  | 'CREATED'
  | 'STATUS_CHANGED'
  | 'ACKNOWLEDGED'
  | 'ESCALATED'
  | 'POST_MORTEM_UPDATED'
  | 'ALERT_TRIGGERED'
  | 'SEVERITY_CHANGED'
  | 'SERVICE_RECOVERED'
  | 'TRACE_ATTACHED';

export interface IncidentEventDto {
  id: string;
  eventType: IncidentEventType;
  message: string;
  actorEmail: string | null;
  createdAt: string;
}

/** A trace attached to an incident, with the hops snapshotted when it was attached. */
export interface AttachedTrace {
  id: string;
  traceId: string;
  note: string | null;
  summary: string;
  hops: Hop[];
  attachedBy: string | null;
  attachedAt: string;
}

export const IncidentsApi = {
  failingTraces: async (orgId: string, incidentId: string) =>
    (await apiClient.get<TraceExplore>(`/organizations/${orgId}/incidents/${incidentId}/failing-traces`)).data,
  attachedTraces: async (orgId: string, incidentId: string) =>
    (await apiClient.get<AttachedTrace[]>(`/organizations/${orgId}/incidents/${incidentId}/traces`)).data,
  attachTrace: async (orgId: string, incidentId: string, traceId: string, note?: string) =>
    (await apiClient.post<AttachedTrace>(`/organizations/${orgId}/incidents/${incidentId}/traces`, { traceId, note })).data,
  detachTrace: async (orgId: string, incidentId: string, traceId: string) => {
    await apiClient.delete(`/organizations/${orgId}/incidents/${incidentId}/traces/${traceId}`);
  },
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

  acknowledge: async (orgId: string, incidentId: string) => {
    const res = await apiClient.post<IncidentDto>(`/organizations/${orgId}/incidents/${incidentId}/acknowledge`);
    return res.data;
  },

  getPostMortem: async (orgId: string, incidentId: string) => {
    const res = await apiClient.get<PostMortemDto>(`/organizations/${orgId}/incidents/${incidentId}/post-mortem`);
    return res.data;
  },

  upsertPostMortem: async (orgId: string, incidentId: string, data: UpsertPostMortemRequest) => {
    const res = await apiClient.put<PostMortemDto>(`/organizations/${orgId}/incidents/${incidentId}/post-mortem`, data);
    return res.data;
  },

  getEvents: async (orgId: string, incidentId: string) => {
    const res = await apiClient.get<IncidentEventDto[]>(`/organizations/${orgId}/incidents/${incidentId}/events`);
    return res.data;
  }
};
