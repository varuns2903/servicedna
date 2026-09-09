import { useQuery } from '@tanstack/react-query';
import { DashboardApi } from '@/api/dashboard.api';
import { useOrganizationStore } from '@/stores/useOrganizationStore';

export function useDashboard() {
  const selectedOrganizationId = useOrganizationStore((state) => state.selectedOrganizationId);

  return useQuery({
    queryKey: ['organizations', selectedOrganizationId, 'dashboard'],
    queryFn: () => DashboardApi.getSummary(selectedOrganizationId!),
    enabled: !!selectedOrganizationId,
  });
}
