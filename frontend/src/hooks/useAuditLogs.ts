import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { AuditLogsApi } from '@/api/auditLogs.api';

export function useAuditLogs(orgId: string | undefined, page: number) {
  return useQuery({
    queryKey: ['organizations', orgId, 'audit-logs', page],
    queryFn: () => AuditLogsApi.getAuditLogs(orgId!, page),
    enabled: !!orgId,
    placeholderData: keepPreviousData,
  });
}
