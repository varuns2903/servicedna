import { apiClient } from './client';
import type { FoundTrace } from './traces.api';
import type { LogEntry } from './logs.api';

/** Everything that touched a business key, across traces and logs. */
export interface Story {
  key: string;
  value: string;
  traceQuery: string;
  logQuery: string | null;
  services: string[];
  firstSeen: string | null;
  lastSeen: string | null;
  traces: FoundTrace[];
  logs: LogEntry[];
  logsUnavailable: string | null;
}

export const FollowApi = {
  follow: async (orgId: string, params: { key: string; value: string; from?: string; to?: string }) =>
    (await apiClient.get<Story>(`/organizations/${orgId}/follow`, { params })).data,
};
