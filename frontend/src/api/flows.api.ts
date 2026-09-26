import { apiClient } from './client';
import type { NodeKind, Protocol } from './graph.api';

export interface FlowOperation {
  /** "nodeId|operation" */
  id: string;
  nodeId: string;
  nodeName: string;
  kind: NodeKind;
  operation: string;
  callsIn: number;
}

export interface FlowCall {
  source: string;
  target: string;
  protocol: Protocol;
  calls: number;
  errors: number;
  callsPerMinute: number;
  errorRate: number | null;
  p50Ms: number | null;
  p95Ms: number | null;
  lastSeen: string | null;
}

export interface FlowDto {
  since: string;
  entry: { nodeId: string; operation: string } | null;
  operations: FlowOperation[];
  calls: FlowCall[];
}

export interface EntryPoint {
  nodeId: string;
  nodeName: string;
  operation: string;
  calls: number;
}

export const FlowsApi = {
  get: async (orgId: string, windowMinutes: number, entry?: { nodeId: string; operation: string }) => {
    const res = await apiClient.get<FlowDto>(`/organizations/${orgId}/flows`, {
      params: { windowMinutes, entryNode: entry?.nodeId, entryOperation: entry?.operation },
    });
    return res.data;
  },
  entryPoints: async (orgId: string, windowMinutes: number) => {
    const res = await apiClient.get<EntryPoint[]>(`/organizations/${orgId}/flows/entry-points`, { params: { windowMinutes } });
    return res.data;
  },
};
