import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { EscalationApi, type UpsertEscalationPolicyRequest } from '@/api/escalation.api';

export function useEscalationPolicy(orgId: string | undefined) {
  return useQuery({
    queryKey: ['organizations', orgId, 'escalation-policy'],
    queryFn: () => EscalationApi.getPolicy(orgId!),
    enabled: !!orgId,
  });
}

export function useUpsertEscalationPolicy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, request }: { orgId: string; request: UpsertEscalationPolicyRequest }) =>
      EscalationApi.upsertPolicy(orgId, request),
    onSuccess: (_, { orgId }) => {
      queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'escalation-policy'] });
    },
  });
}
