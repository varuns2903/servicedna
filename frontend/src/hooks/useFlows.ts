import { useQuery } from '@tanstack/react-query';
import { FlowsApi } from '@/api/flows.api';
import { useOrganizationStore } from '@/stores/useOrganizationStore';

export function useEntryPoints(windowMinutes: number) {
  const orgId = useOrganizationStore((s) => s.selectedOrganizationId);
  return useQuery({
    queryKey: ['organizations', orgId, 'flows', 'entry-points', windowMinutes],
    queryFn: () => FlowsApi.entryPoints(orgId!, windowMinutes),
    enabled: !!orgId,
    refetchInterval: 30_000,
  });
}

export function useFlow(windowMinutes: number, entry: { nodeId: string; operation: string } | null) {
  const orgId = useOrganizationStore((s) => s.selectedOrganizationId);
  return useQuery({
    queryKey: ['organizations', orgId, 'flows', windowMinutes, entry?.nodeId, entry?.operation],
    queryFn: () => FlowsApi.get(orgId!, windowMinutes, entry ?? undefined),
    enabled: !!orgId,
    refetchInterval: 30_000,
  });
}
