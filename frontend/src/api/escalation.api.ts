import { apiClient } from './client';

export interface EscalationPolicyDto {
  escalationEmail: string | null;
  escalateAfterMinutes: number;
}

export interface UpsertEscalationPolicyRequest {
  escalationEmail: string;
  escalateAfterMinutes: number;
}

export const EscalationApi = {
  getPolicy: async (orgId: string): Promise<EscalationPolicyDto> => {
    const { data } = await apiClient.get<EscalationPolicyDto>(
      `/organizations/${orgId}/escalation-policy`
    );
    return data;
  },

  upsertPolicy: async (
    orgId: string,
    request: UpsertEscalationPolicyRequest
  ): Promise<EscalationPolicyDto> => {
    const { data } = await apiClient.put<EscalationPolicyDto>(
      `/organizations/${orgId}/escalation-policy`,
      request
    );
    return data;
  },
};
