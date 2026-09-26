import { apiClient } from './client';

export interface TraceSummary {
  traceId: string;
  rootService: string | null;
  rootOperation: string | null;
  start: string;
  durationMs: number;
  error: boolean;
}

export interface TraceSpan {
  spanId: string;
  parentSpanId: string | null;
  service: string;
  name: string;
  kind: string;
  start: string;
  durationMs: number;
  error: boolean;
  statusMessage: string | null;
  attributes: Record<string, string>;
  events: { name: string; time: string; attributes: Record<string, string> }[];
}

export interface Trace {
  traceId: string;
  start: string;
  durationMs: number;
  spans: TraceSpan[];
}

export interface TraceSearch {
  service?: string;
  operation?: string;
  calleeService?: string;
  calleeOperation?: string;
  errorsOnly?: boolean;
  windowMinutes?: number;
  limit?: number;
}

export const TracesApi = {
  search: async (orgId: string, params: TraceSearch) => {
    const res = await apiClient.get<TraceSummary[]>(`/organizations/${orgId}/traces`, { params });
    return res.data;
  },
  get: async (orgId: string, traceId: string) => {
    const res = await apiClient.get<Trace>(`/organizations/${orgId}/traces/${traceId}`);
    return res.data;
  },
};
