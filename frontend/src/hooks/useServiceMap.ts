import { useQuery } from '@tanstack/react-query';
import { MapApi } from '@/api/map.api';
import { useOrganizationStore } from '@/stores/useOrganizationStore';

export function useServiceMap() {
  const selectedOrganizationId = useOrganizationStore((state) => state.selectedOrganizationId);

  return useQuery({
    queryKey: ['organization', selectedOrganizationId, 'map'],
    queryFn: () => MapApi.getServiceMap(selectedOrganizationId!),
    enabled: !!selectedOrganizationId,
  });
}
