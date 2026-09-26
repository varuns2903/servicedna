import { useQuery } from '@tanstack/react-query';
import { TracesApi, type TraceSearch } from '@/api/traces.api';
import { useOrganizationStore } from '@/stores/useOrganizationStore';

export function useTraceSearch(params: TraceSearch, enabled = true) {
  const orgId = useOrganizationStore((s) => s.selectedOrganizationId);
  return useQuery({
    queryKey: ['organizations', orgId, 'traces', params],
    queryFn: () => TracesApi.search(orgId!, params),
    enabled: !!orgId && enabled,
  });
}

export function useTrace(traceId: string | null) {
  const orgId = useOrganizationStore((s) => s.selectedOrganizationId);
  return useQuery({
    queryKey: ['organizations', orgId, 'trace', traceId],
    queryFn: () => TracesApi.get(orgId!, traceId!),
    enabled: !!orgId && !!traceId,
  });
}
