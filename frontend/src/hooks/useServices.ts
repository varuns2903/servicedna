import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ServicesApi, type CreateServiceRequest } from '@/api/services.api';
import { useOrganizationStore } from '@/stores/useOrganizationStore';

export function useServices() {
  const selectedOrganizationId = useOrganizationStore((state) => state.selectedOrganizationId);

  return useQuery({
    queryKey: ['organizations', selectedOrganizationId, 'services'],
    queryFn: () => ServicesApi.getServices(selectedOrganizationId!),
    enabled: !!selectedOrganizationId,
  });
}

export function useCreateService() {
  const queryClient = useQueryClient();
  const selectedOrganizationId = useOrganizationStore((state) => state.selectedOrganizationId);

  return useMutation({
    mutationFn: (request: CreateServiceRequest) =>
      ServicesApi.createService(selectedOrganizationId!, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations', selectedOrganizationId, 'services'] });
    },
  });
}
