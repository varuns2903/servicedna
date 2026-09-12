import { useQuery } from '@tanstack/react-query';
import { AnalyticsApi } from '@/api/analytics.api';

export function useSlaReport(orgId?: string, days = 30) {
  return useQuery({
    queryKey: ['organizations', orgId, 'analytics', 'sla', days],
    queryFn: () => AnalyticsApi.getSlaReport(orgId!, days),
    enabled: !!orgId,
  });
}
