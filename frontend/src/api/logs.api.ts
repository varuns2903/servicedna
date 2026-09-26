import { apiClient } from './client';

/** Log search filters; all optional. `attribute` entries look like `orderId=o-17`. */
export interface LogSearchParams {
  service?: string;
  environment?: string;
  level?: 'debug' | 'info' | 'warn' | 'error';
  text?: string;
  traceId?: string;
  attribute?: string[];
  /** Raw LogQL; overrides the filters. */
  q?: string;
  from?: string;
  to?: string;
  limit?: number;
}

export interface LogEntry {
  time: string;
  service: string | null;
  environment: string | null;
  level: string | null;
  body: string;
  traceId: string | null;
  spanId: string | null;
  attributes: Record<string, string>;
}

export interface LogSearch {
  query: string;
  entries: LogEntry[];
}

export const LogsApi = {
  search: async (orgId: string, params: LogSearchParams) => {
    const res = await apiClient.get<LogSearch>(`/organizations/${orgId}/logs`, { params, paramsSerializer: { indexes: null } });
    return res.data;
  },
};
