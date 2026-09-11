import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { WebhooksApi } from '@/api/webhooks.api';
import type { CreateWebhookRequest } from '@/api/webhooks.api';

export function useWebhooks(orgId?: string) {
  return useQuery({
    queryKey: ['organizations', orgId, 'webhooks'],
    queryFn: () => WebhooksApi.getAll(orgId!),
    enabled: !!orgId,
  });
}

export function useCreateWebhook() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, request }: { orgId: string; request: CreateWebhookRequest }) =>
      WebhooksApi.create(orgId, request),
    onSuccess: (_, { orgId }) => {
      queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'webhooks'] });
    },
  });
}

export function useDeleteWebhook() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, webhookId }: { orgId: string; webhookId: string }) =>
      WebhooksApi.delete(orgId, webhookId),
    onSuccess: (_, { orgId }) => {
      queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'webhooks'] });
    },
  });
}
