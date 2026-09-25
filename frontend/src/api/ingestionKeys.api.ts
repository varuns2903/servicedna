import { apiClient } from './client';

export interface IngestionKeyDto {
  id: string;
  name: string;
  keyPrefix: string;
  /** Only present in the response that creates the key. */
  key: string | null;
  createdByEmail: string | null;
  createdAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
}

export const IngestionKeysApi = {
  getAll: async (orgId: string) => {
    const res = await apiClient.get<IngestionKeyDto[]>(`/organizations/${orgId}/ingestion-keys`);
    return res.data;
  },

  create: async (orgId: string, name: string) => {
    const res = await apiClient.post<IngestionKeyDto>(`/organizations/${orgId}/ingestion-keys`, { name });
    return res.data;
  },

  revoke: async (orgId: string, keyId: string) => {
    const res = await apiClient.delete<IngestionKeyDto>(`/organizations/${orgId}/ingestion-keys/${keyId}`);
    return res.data;
  },
};
