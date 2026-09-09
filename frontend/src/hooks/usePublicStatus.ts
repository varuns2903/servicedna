import { useQuery } from '@tanstack/react-query';
import { PublicApi } from '@/api/public.api';

export function usePublicStatus(orgId?: string) {
  return useQuery({
    queryKey: ['public', 'status', orgId],
    queryFn: () => PublicApi.getStatusPage(orgId!),
    enabled: !!orgId,
    staleTime: 30000, // 30 seconds
    refetchInterval: 30000, // auto-refresh every 30s for the public view
  });
}
