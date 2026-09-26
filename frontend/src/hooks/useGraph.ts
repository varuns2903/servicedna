import { useQuery } from '@tanstack/react-query';
import { GraphApi } from '@/api/graph.api';
import { useOrganizationStore } from '@/stores/useOrganizationStore';

export function useGraph(windowMinutes: number) {
  const orgId = useOrganizationStore((state) => state.selectedOrganizationId);
  return useQuery({
    queryKey: ['organizations', orgId, 'graph', windowMinutes],
    queryFn: () => GraphApi.get(orgId!, windowMinutes),
    enabled: !!orgId,
    refetchInterval: 30_000,
  });
}
