import { apiClient } from './client';

export interface GitHubAppInstallation {
  installationId: number;
  account: string;
  installedAt: string;
  lastSyncedAt: string | null;
}

export interface GitHubAppStatus {
  /** False when this ServiceDNA has no GitHub App set up (GITHUB_APP_* settings). */
  configured: boolean;
  slug: string | null;
  /** Only for owners and admins. */
  installUrl: string | null;
  installations: GitHubAppInstallation[];
}

export interface GitHubSyncResult {
  repository: string;
  service: string | null;
  ok: boolean;
  message: string;
}

export const GitHubAppApi = {
  status: async (orgId: string) => (await apiClient.get<GitHubAppStatus>(`/organizations/${orgId}/github/app`)).data,
  connect: async (orgId: string, body: { installationId: number; code: string; state: string }) =>
    (await apiClient.post<GitHubAppInstallation>(`/organizations/${orgId}/github/installations`, body)).data,
  disconnect: async (orgId: string, installationId: number) => {
    await apiClient.delete(`/organizations/${orgId}/github/installations/${installationId}`);
  },
  sync: async (orgId: string, installationId: number) =>
    (await apiClient.post<GitHubSyncResult[]>(`/organizations/${orgId}/github/installations/${installationId}/sync`)).data,
};
