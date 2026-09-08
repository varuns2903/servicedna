import { useState } from 'react';
import { useServices } from '@/hooks/useServices';
import { useAlertRules, useCreateAlertRule, useDeleteAlertRule } from '@/hooks/useAlerts';
import { useOrganizationStore } from '@/stores/useOrganizationStore';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Trash2, Plus, Bell } from 'lucide-react';
import type { AlertConditionType, IntegrationType } from '@/api/alerts.api';

export function AlertsList() {
  const currentOrgId = useOrganizationStore((state) => state.selectedOrganizationId);
  const { data: services, isLoading: servicesLoading } = useServices();
  
  const [selectedServiceId, setSelectedServiceId] = useState<string>('');

  const { data: alerts, isLoading: alertsLoading } = useAlertRules(currentOrgId || undefined, selectedServiceId || undefined);
  
  const createAlert = useCreateAlertRule();
  const deleteAlert = useDeleteAlertRule();

  // Form State
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [type, setType] = useState<AlertConditionType>('LATENCY_ABOVE');
  const [threshold, setThreshold] = useState(1000);
  const [duration, setDuration] = useState(5);
  const [integration, setIntegration] = useState<IntegrationType>('WEBHOOK');
  const [webhookUrl, setWebhookUrl] = useState('');

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentOrgId || !selectedServiceId) return;

    createAlert.mutate({
      orgId: currentOrgId,
      serviceId: selectedServiceId,
      data: {
        condition: { type, threshold, durationMinutes: duration },
        integrationType: integration,
        webhookUrl
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
                        value={type} 
                        onChange={(e) => setType(e.target.value as AlertConditionType)}
                        className="w-full bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white"
                      >
                        <option value="LATENCY_ABOVE">Latency Above</option>
                        <option value="ERROR_RATE_ABOVE">Error Rate Above</option>
                        <option value="STATUS_DOWN">Status Down</option>
                      </select>
                    </div>
                    {type !== 'STATUS_DOWN' && (
                      <div>
                        <label className="block text-sm text-gray-400 mb-1">Threshold</label>
                        <input 
                          type="number" 
                          value={threshold} 
                          onChange={(e) => setThreshold(Number(e.target.value))}
                          className="w-full bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white"
                        />
                      </div>
                    )}
                    <div>
                      <label className="block text-sm text-gray-400 mb-1">Duration (minutes)</label>
                      <input 
                        type="number" 
                        value={duration} 
                        onChange={(e) => setDuration(Number(e.target.value))}
                        className="w-full bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white"
                      />
                    </div>
                    <div>
                      <label className="block text-sm text-gray-400 mb-1">Integration</label>
                      <select 
                        value={integration} 
                        onChange={(e) => setIntegration(e.target.value as IntegrationType)}
                        className="w-full bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white"
                      >
                        <option value="WEBHOOK">Webhook</option>
                        <option value="SLACK">Slack</option>
                        <option value="PAGERDUTY">PagerDuty</option>
                        <option value="EMAIL">Email</option>
                      </select>
                    </div>
                    <div className="col-span-2">
                      <label className="block text-sm text-gray-400 mb-1">Target URL / Address</label>
                      <input 
                        type="text" 
                        required
                        value={webhookUrl} 
                        onChange={(e) => setWebhookUrl(e.target.value)}
                        placeholder="https://..."
                        className="w-full bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-white"
                      />
                    </div>
                  </div>
                  <div className="flex justify-end space-x-3 pt-4">
                    <Button type="button" variant="outline" onClick={() => setIsFormOpen(false)}>Cancel</Button>
                    <Button type="submit" disabled={createAlert.isPending}>
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
                        {alert.condition.type.replace(/_/g, ' ')} 
                        {alert.condition.type !== 'STATUS_DOWN' && ` > ${alert.condition.threshold}`}
                      </h4>
                      <p className="text-sm text-gray-400">
                        For {alert.condition.durationMinutes} minutes • {alert.integrationType} ({alert.webhookUrl})
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
