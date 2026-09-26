import { apiClient } from './client';
import type { ServiceStatus } from '@/components/status/StatusIndicator';

export type NodeKind = 'SERVICE' | 'DATABASE' | 'EXTERNAL' | 'TOPIC';
export type Protocol = 'HTTP' | 'GRPC' | 'GRAPHQL' | 'MESSAGING' | 'DATABASE' | 'OTHER';

export interface GraphNode {
  /** Service id for services, `KIND:name` for databases, external hosts and topics. */
  id: string;
  name: string;
  kind: NodeKind;
  status: ServiceStatus | null;
  environment: string | null;
  language: string | null;
}

export interface GraphEdge {
  source: string;
  target: string;
  /** Entered by a person. */
  declared: boolean;
  /** Seen in traces within the window. */
  observed: boolean;
  protocols: Protocol[];
  calls: number;
  errors: number;
  callsPerMinute: number;
  errorRate: number | null;
  avgMs: number | null;
  p95Ms: number | null;
  lastSeen: string | null;
}

export interface GraphDto {
  since: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export const GraphApi = {
  get: async (orgId: string, windowMinutes: number) => {
    const res = await apiClient.get<GraphDto>(`/organizations/${orgId}/graph`, { params: { windowMinutes } });
    return res.data;
  },
};
