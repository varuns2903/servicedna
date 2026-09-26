import { useQuery } from '@tanstack/react-query';
import { LogsApi, type LogSearchParams } from '@/api/logs.api';
import { useOrganizationStore } from '@/stores/useOrganizationStore';

export function useLogSearch(params: LogSearchParams | null) {
  const orgId = useOrganizationStore((s) => s.selectedOrganizationId);
  return useQuery({
    queryKey: ['organizations', orgId, 'logs', params],
    queryFn: () => LogsApi.search(orgId!, params!),
    enabled: !!orgId && !!params,
    retry: false,
  });
}

/** A trace's logs: from a little before it started to a few minutes after (logs can lag). */
export function useTraceLogs(traceId: string | null, start: string | null, refetchInterval?: number | false) {
  const orgId = useOrganizationStore((s) => s.selectedOrganizationId);
  const from = start ? new Date(new Date(start).getTime() - 60_000).toISOString() : undefined;
  const to = start ? new Date(new Date(start).getTime() + 10 * 60_000).toISOString() : undefined;
  return useQuery({
    queryKey: ['organizations', orgId, 'logs', 'trace', traceId],
    queryFn: () => LogsApi.search(orgId!, { traceId: traceId!, from, to, limit: 500 }),
    enabled: !!orgId && !!traceId && !!start,
    retry: false,
    refetchInterval,
  });
}
