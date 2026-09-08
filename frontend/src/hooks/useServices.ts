import { useQuery } from '@tanstack/react-query';
import { ServicesApi } from '@/api/services.api';
import { useOrganizationStore } from '@/stores/useOrganizationStore';

export function useServices() {
  const selectedOrganizationId = useOrganizationStore((state) => state.selectedOrganizationId);

  return useQuery({
    queryKey: ['organization', selectedOrganizationId, 'services'],
    queryFn: () => ServicesApi.getServices(selectedOrganizationId!),
    enabled: !!selectedOrganizationId,
  });
}
