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

/** Explorer filters; all optional. `attribute` entries look like `orderId=o-17` or `http.response.status_code>=500`. */
export interface TraceExploreParams {
  service?: string;
  operation?: string;
  environment?: string;
  status?: 'error' | 'ok';
  minDurationMs?: number;
  maxDurationMs?: number;
  attribute?: string[];
  text?: string;
  /** Raw TraceQL; overrides the filters. */
  q?: string;
  from?: string;
  to?: string;
  limit?: number;
}

export interface MatchedSpan {
  spanId: string;
  service: string | null;
  name: string | null;
  start: string;
  durationMs: number;
  error: boolean;
  attributes: Record<string, string>;
}

export interface FoundTrace {
  traceId: string;
  rootService: string | null;
  rootOperation: string | null;
  start: string;
  durationMs: number;
  errors: number;
  services: Record<string, { spans: number; errors: number }>;
  matched: number;
  spans: MatchedSpan[];
}

export interface TraceExplore {
  query: string;
  traces: FoundTrace[];
}

export const TracesApi = {
  search: async (orgId: string, params: TraceSearch) => {
    const res = await apiClient.get<TraceSummary[]>(`/organizations/${orgId}/traces`, { params });
    return res.data;
  },
  explore: async (orgId: string, params: TraceExploreParams) => {
    const res = await apiClient.get<TraceExplore>(`/organizations/${orgId}/traces/explore`, {
      params,
      // Repeat attribute=… rather than attribute[]=…
      paramsSerializer: { indexes: null },
    });
    return res.data;
  },
  attributes: async (orgId: string) => {
    const res = await apiClient.get<string[]>(`/organizations/${orgId}/traces/attributes`);
    return res.data;
  },
  attributeValues: async (orgId: string, name: string) => {
    const res = await apiClient.get<string[]>(`/organizations/${orgId}/traces/attributes/values`, { params: { name } });
    return res.data;
  },
  get: async (orgId: string, traceId: string) => {
    const res = await apiClient.get<Trace>(`/organizations/${orgId}/traces/${traceId}`);
    return res.data;
  },
};
