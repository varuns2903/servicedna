import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { OnCallApi, type UpsertOnCallRotationRequest } from '@/api/oncall.api';

export function useOnCallRotation(orgId?: string) {
  return useQuery({
    queryKey: ['organizations', orgId, 'on-call'],
    queryFn: () => OnCallApi.getRotation(orgId!),
    enabled: !!orgId,
  });
}

export function useUpsertOnCallRotation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, request }: { orgId: string; request: UpsertOnCallRotationRequest }) =>
      OnCallApi.upsertRotation(orgId, request),
    onSuccess: (_, { orgId }) => {
      queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'on-call'] });
    },
  });
}
