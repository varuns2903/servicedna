import { useState } from 'react';
import { useServices } from '@/hooks/useServices';
import { useAlertRules, useCreateAlertRule, useDeleteAlertRule } from '@/hooks/useAlerts';
import { useOrganizationStore } from '@/stores/useOrganizationStore';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Trash2, Plus, Bell } from 'lucide-react';
import { ALERT_CONDITION_LABELS, INTEGRATION_TYPE_LABELS, describeAlertRuleActions } from '@/api/alerts.api';
import type { AlertCondition, IntegrationType } from '@/api/alerts.api';
import type { IncidentSeverity } from '@/api/incidents.api';

const INCIDENT_SEVERITIES: IncidentSeverity[] = ['CRITICAL', 'MAJOR', 'MINOR', 'LOW'];

export function AlertsList() {
  const currentOrgId = useOrganizationStore((state) => state.selectedOrganizationId);
  const { data: services, isLoading: servicesLoading } = useServices();
  
  const [selectedServiceId, setSelectedServiceId] = useState<string>('');

  const { data: alerts, isLoading: alertsLoading } = useAlertRules(currentOrgId || undefined, selectedServiceId || undefined);
  
  const createAlert = useCreateAlertRule();
  const deleteAlert = useDeleteAlertRule();

  // Form State
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [condition, setCondition] = useState<AlertCondition>('STATUS_DOWN');
  const [integration, setIntegration] = useState<IntegrationType>('GENERIC');
  const [webhookUrl, setWebhookUrl] = useState('');
  const [incidentSeverity, setIncidentSeverity] = useState<IncidentSeverity | ''>('CRITICAL');

  // Recovery can't open an incident: incidents opened by alerts resolve on recovery by themselves.
  const canOpenIncident = condition !== 'STATUS_RECOVERED';
  const severity = canOpenIncident && incidentSeverity ? incidentSeverity : undefined;
  const hasAction = webhookUrl.trim() !== '' || severity !== undefined;

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentOrgId || !selectedServiceId) return;

    createAlert.mutate({
      orgId: currentOrgId,
      serviceId: selectedServiceId,
      data: {
        condition,
        integrationType: integration,
        webhookUrl: webhookUrl.trim() || undefined,
        incidentSeverity: severity,
      }
    }, {
      onSuccess: () => {
        setIsFormOpen(false);
        setWebhookUrl('');
      }
    });
  };

  const handleDelete = (ruleId: string) => {
    if (!currentOrgId || !selectedServiceId) return;
    deleteAlert.mutate({ orgId: currentOrgId, serviceId: selectedServiceId, ruleId });
  };

  if (servicesLoading) {
    return <div className="p-8 text-gray-400">Loading services...</div>;
  }

  return (
    <div className="p-8 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-white">Alert Rules</h1>
      </div>

      <Card>
        <CardContent className="p-6">
          <label className="block text-sm font-medium text-gray-400 mb-2">Select a Service to Configure Alerts</label>
          <select 
            className="w-full max-w-md bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white focus:border-emerald-500 focus:outline-none"
            value={selectedServiceId}
            onChange={(e) => setSelectedServiceId(e.target.value)}
          >
            <option value="">-- Select a Service --</option>
            {services?.map(s => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
        </CardContent>
      </Card>

      {selectedServiceId && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-medium text-white">Configured Rules</h2>
            <Button onClick={() => setIsFormOpen(!isFormOpen)} className="flex items-center space-x-2">
              <Plus className="h-4 w-4" />
              <span>New Rule</span>
            </Button>
          </div>

          {isFormOpen && (
            <Card className="border-emerald-500/50">
              <CardHeader>
                <CardTitle>Create New Alert Rule</CardTitle>
              </CardHeader>
              <CardContent>
                <form onSubmit={handleCreate} className="space-y-4">
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm text-gray-400 mb-1">Condition</label>
                      <select 
                        value={condition} 
                        onChange={(e) => setCondition(e.target.value as AlertCondition)}
                        className="w-full bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white"
                      >
                        {Object.entries(ALERT_CONDITION_LABELS).map(([value, label]) => (
                          <option key={value} value={value}>{label}</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-sm text-gray-400 mb-1">Integration</label>
                      <select 
                        value={integration} 
                        onChange={(e) => setIntegration(e.target.value as IntegrationType)}
                        className="w-full bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white"
                      >
                        {Object.entries(INTEGRATION_TYPE_LABELS).map(([value, label]) => (
                          <option key={value} value={value}>{label}</option>
                        ))}
                      </select>
                    </div>
                    {canOpenIncident && (
                      <div className="col-span-2">
                        <label className="block text-sm text-gray-400 mb-1">Open incident</label>
                        <select
                          value={incidentSeverity}
                          onChange={(e) => setIncidentSeverity(e.target.value as IncidentSeverity | '')}
                          className="w-full bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white"
                        >
                          <option value="">Don't open an incident</option>
                          {INCIDENT_SEVERITIES.map((s) => (
                            <option key={s} value={s}>Open a {s} incident</option>
                          ))}
                        </select>
                        <p className="mt-1 text-xs text-gray-500">
                          Repeat alerts update the same open incident, and it resolves automatically when the service recovers.
                        </p>
                      </div>
                    )}
                    <div className="col-span-2">
                      <label className="block text-sm text-gray-400 mb-1">Webhook URL {severity && '(optional)'}</label>
                      <input 
                        type="url" 
                        required={!severity}
                        value={webhookUrl} 
                        onChange={(e) => setWebhookUrl(e.target.value)}
                        placeholder="https://..."
                        className="w-full bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white"
                      />
                    </div>
                  </div>
                  <div className="flex justify-end space-x-3 pt-4">
                    <Button type="button" variant="outline" onClick={() => setIsFormOpen(false)}>Cancel</Button>
                    <Button type="submit" disabled={createAlert.isPending || !hasAction}>
                      {createAlert.isPending ? 'Saving...' : 'Save Rule'}
                    </Button>
                  </div>
                </form>
              </CardContent>
            </Card>
          )}

          {alertsLoading ? (
            <div className="text-gray-400">Loading rules...</div>
          ) : alerts?.length === 0 ? (
            <div className="text-center p-8 border border-dashed border-charcoal-700 rounded-lg text-gray-400">
              <Bell className="mx-auto h-8 w-8 text-gray-600 mb-2" />
              <p>No alert rules configured for this service.</p>
            </div>
          ) : (
            <div className="grid gap-4">
              {alerts?.map(alert => (
                <Card key={alert.id}>
                  <CardContent className="p-4 flex items-center justify-between">
                    <div>
                      <h4 className="text-white font-medium">
                        {ALERT_CONDITION_LABELS[alert.condition] ?? alert.condition}
                      </h4>
                      <p className="text-sm text-gray-400">
                        {describeAlertRuleActions(alert)}
                      </p>
                    </div>
                    <Button 
                      variant="outline" 
                      onClick={() => handleDelete(alert.id)}
                      className="text-rose-400 hover:text-rose-300 hover:bg-rose-500/10 border-rose-500/20"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
