import { apiClient } from './client';

export type AlertConditionType = 'LATENCY_ABOVE' | 'ERROR_RATE_ABOVE' | 'STATUS_DOWN';
export type IntegrationType = 'WEBHOOK' | 'SLACK' | 'PAGERDUTY' | 'EMAIL';

export interface AlertCondition {
  type: AlertConditionType;
  threshold: number;
  durationMinutes: number;
}

export interface AlertRuleDto {
  id: string;
  organizationId: string;
  serviceId: string;
  condition: AlertCondition;
  webhookUrl: string;
  integrationType: IntegrationType;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAlertRuleRequest {
  condition: AlertCondition;
  webhookUrl: string;
  integrationType: IntegrationType;
}

export const AlertsApi = {
  create: async (orgId: string, serviceId: string, data: CreateAlertRuleRequest) => {
    const res = await apiClient.post<AlertRuleDto>(`/organizations/${orgId}/services/${serviceId}/alert-rules`, data);
    return res.data;
  },

  getAllForService: async (orgId: string, serviceId: string) => {
    const res = await apiClient.get<AlertRuleDto[]>(`/organizations/${orgId}/services/${serviceId}/alert-rules`);
    return res.data;
  },

  delete: async (orgId: string, serviceId: string, ruleId: string) => {
    await apiClient.delete(`/organizations/${orgId}/services/${serviceId}/alert-rules/${ruleId}`);
  }
};
