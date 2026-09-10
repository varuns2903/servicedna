import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ServicesApi,
  type CreateServiceRequest,
  type UpdateServiceRequest,
  type MetricsRange,
} from '@/api/services.api';
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

export function useUpdateService() {
  const queryClient = useQueryClient();
  const selectedOrganizationId = useOrganizationStore((state) => state.selectedOrganizationId);

  return useMutation({
    mutationFn: ({ serviceId, request }: { serviceId: string; request: UpdateServiceRequest }) =>
      ServicesApi.updateService(selectedOrganizationId!, serviceId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations', selectedOrganizationId, 'services'] });
    },
  });
}

export function useDeleteService() {
  const queryClient = useQueryClient();
  const selectedOrganizationId = useOrganizationStore((state) => state.selectedOrganizationId);

  return useMutation({
    mutationFn: (serviceId: string) => ServicesApi.deleteService(selectedOrganizationId!, serviceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations', selectedOrganizationId, 'services'] });
    },
  });
}

export function useServiceMetrics(serviceId: string | undefined, range: MetricsRange) {
  const selectedOrganizationId = useOrganizationStore((state) => state.selectedOrganizationId);

  return useQuery({
    queryKey: ['organizations', selectedOrganizationId, 'services', serviceId, 'metrics', range],
    queryFn: () => ServicesApi.getServiceMetrics(selectedOrganizationId!, serviceId!, range),
    enabled: !!selectedOrganizationId && !!serviceId,
  });
}

export function useRegenerateApiKey() {
  const queryClient = useQueryClient();
  const selectedOrganizationId = useOrganizationStore((state) => state.selectedOrganizationId);

  return useMutation({
    mutationFn: (serviceId: string) =>
      ServicesApi.regenerateApiKey(selectedOrganizationId!, serviceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations', selectedOrganizationId, 'services'] });
    },
  });
}
