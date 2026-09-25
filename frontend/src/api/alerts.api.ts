import { apiClient } from './client';

// Mirrors the backend's AlertCondition / IntegrationType enums: rules fire on service status
// transitions, and are serialized as plain enum strings.
export type AlertCondition = 'STATUS_DOWN' | 'STATUS_DEGRADED' | 'STATUS_RECOVERED';
export type IntegrationType = 'GENERIC' | 'SLACK' | 'DISCORD';

export const ALERT_CONDITION_LABELS: Record<AlertCondition, string> = {
  STATUS_DOWN: 'Status Down',
  STATUS_DEGRADED: 'Status Degraded',
  STATUS_RECOVERED: 'Status Recovered',
};

export const INTEGRATION_TYPE_LABELS: Record<IntegrationType, string> = {
  GENERIC: 'Generic Webhook',
  SLACK: 'Slack',
  DISCORD: 'Discord',
};

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
