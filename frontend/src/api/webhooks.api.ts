import { apiClient } from './client';

export type WebhookType = 'SLACK' | 'TEAMS' | 'GENERIC';

export interface WebhookDto {
  id: string;
  url: string;
  webhookType: WebhookType;
  createdAt: string;
}

export interface CreateWebhookRequest {
  url: string;
  webhookType: WebhookType;
}

export const WebhooksApi = {
  getAll: async (orgId: string): Promise<WebhookDto[]> => {
    const { data } = await apiClient.get<WebhookDto[]>(`/organizations/${orgId}/webhooks`);
    return data;
  },

  create: async (orgId: string, request: CreateWebhookRequest): Promise<WebhookDto> => {
    const { data } = await apiClient.post<WebhookDto>(`/organizations/${orgId}/webhooks`, request);
    return data;
  },

  delete: async (orgId: string, webhookId: string): Promise<void> => {
    await apiClient.delete(`/organizations/${orgId}/webhooks/${webhookId}`);
  },
};
