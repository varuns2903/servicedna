import { apiClient } from './client';
import type { IncidentSeverity } from './incidents.api';

// Mirrors the backend's AlertCondition / IntegrationType enums, serialized as plain strings.
// STATUS_* rules fire on status transitions; the rest are thresholds evaluated on recent checks.
export type AlertCondition =
  | 'STATUS_DOWN'
  | 'STATUS_DEGRADED'
  | 'STATUS_RECOVERED'
  | 'LATENCY_ABOVE'
  | 'ERROR_RATE_ABOVE'
  | 'CONSECUTIVE_FAILURES'
  /** The service started calling a service it doesn't declare as a dependency. */
  | 'UNDECLARED_DEPENDENCY';
export type IntegrationType = 'GENERIC' | 'SLACK' | 'DISCORD';

export const ALERT_CONDITION_LABELS: Record<AlertCondition, string> = {
  STATUS_DOWN: 'Status Down',
  STATUS_DEGRADED: 'Status Degraded',
  STATUS_RECOVERED: 'Status Recovered',
  LATENCY_ABOVE: 'Latency Above',
  ERROR_RATE_ABOVE: 'Error Rate Above',
  CONSECUTIVE_FAILURES: 'Consecutive Failures',
  UNDECLARED_DEPENDENCY: 'Calls an Undeclared Dependency',
};

export const THRESHOLD_UNITS: Partial<Record<AlertCondition, string>> = {
  LATENCY_ABOVE: 'ms',
  ERROR_RATE_ABOVE: '%',
  CONSECUTIVE_FAILURES: 'failed checks',
};

export const isThresholdCondition = (c: AlertCondition) => c in THRESHOLD_UNITS;
export const usesWindow = (c: AlertCondition) => c === 'LATENCY_ABOVE' || c === 'ERROR_RATE_ABOVE';

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
  webhookUrl: string | null;
  integrationType: IntegrationType;
  /** When set, a matching status change opens or updates an incident with this severity. */
  incidentSeverity: IncidentSeverity | null;
  threshold: number | null;
  windowMinutes: number | null;
  /** Threshold rules only: whether the threshold is currently exceeded. */
  breached: boolean;
  /** "CATALOG" when the service's servicedna.yaml manages the rule (edit it there); null for rules made here. */
  managedBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAlertRuleRequest {
  condition: AlertCondition;
  webhookUrl?: string;
  integrationType: IntegrationType;
  incidentSeverity?: IncidentSeverity;
  threshold?: number;
  windowMinutes?: number;
}

/** e.g. "Latency above 1000 ms (avg over 5 min)" or "Status Down". */
export function describeAlertRuleCondition(rule: AlertRuleDto): string {
  const label = ALERT_CONDITION_LABELS[rule.condition] ?? rule.condition;
  if (rule.threshold == null) return label;
  if (rule.condition === 'CONSECUTIVE_FAILURES') return `${rule.threshold} consecutive failed checks`;
  const window = rule.windowMinutes ? ` (avg over ${rule.windowMinutes} min)` : '';
  return `${label} ${rule.threshold} ${THRESHOLD_UNITS[rule.condition]}${window}`;
}

/** One-line summary of what a rule does when it fires, e.g. "Slack webhook · Opens CRITICAL incident". */
export function describeAlertRuleActions(rule: AlertRuleDto): string {
  const actions: string[] = [];
  if (rule.webhookUrl) {
    actions.push(`${INTEGRATION_TYPE_LABELS[rule.integrationType] ?? rule.integrationType} (${rule.webhookUrl})`);
  }
  if (rule.incidentSeverity) {
    actions.push(`Opens ${rule.incidentSeverity} incident`);
  }
  return actions.join(' · ');
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
