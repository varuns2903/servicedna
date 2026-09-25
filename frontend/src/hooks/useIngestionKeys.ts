import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { IngestionKeysApi } from '@/api/ingestionKeys.api';

export function useIngestionKeys(orgId?: string) {
  return useQuery({
    queryKey: ['organizations', orgId, 'ingestion-keys'],
    queryFn: () => IngestionKeysApi.getAll(orgId!),
    enabled: !!orgId,
  });
}

export function useCreateIngestionKey() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, name }: { orgId: string; name: string }) => IngestionKeysApi.create(orgId, name),
    onSuccess: (_, { orgId }) => {
      queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'ingestion-keys'] });
    },
  });
}

export function useRevokeIngestionKey() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, keyId }: { orgId: string; keyId: string }) => IngestionKeysApi.revoke(orgId, keyId),
    onSuccess: (_, { orgId }) => {
      queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'ingestion-keys'] });
    },
  });
}
