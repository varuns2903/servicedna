import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AlertsApi } from '@/api/alerts.api';
import type { CreateAlertRuleRequest } from '@/api/alerts.api';

export function useAlertRules(orgId?: string, serviceId?: string) {
  return useQuery({
    queryKey: ['organizations', orgId, 'services', serviceId, 'alerts'],
    queryFn: () => AlertsApi.getAllForService(orgId!, serviceId!),
    enabled: !!orgId && !!serviceId,
  });
}

export function useCreateAlertRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, serviceId, data }: { orgId: string; serviceId: string; data: CreateAlertRuleRequest }) =>
      AlertsApi.create(orgId, serviceId, data),
    onSuccess: (_, { orgId, serviceId }) => {
      queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'services', serviceId, 'alerts'] });
    },
  });
}

export function useDeleteAlertRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, serviceId, ruleId }: { orgId: string; serviceId: string; ruleId: string }) =>
      AlertsApi.delete(orgId, serviceId, ruleId),
    onSuccess: (_, { orgId, serviceId }) => {
      queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'services', serviceId, 'alerts'] });
    },
  });
}
