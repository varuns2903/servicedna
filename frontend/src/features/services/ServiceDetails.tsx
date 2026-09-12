import { useEffect, useState } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import axios from 'axios';
import { ArrowLeft, Copy, Check, AlertTriangle, Trash2, Bell } from 'lucide-react';
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { StatusIndicator } from '@/components/status/StatusIndicator';
import {
  useService,
  useUpdateService,
  useDeleteService,
  useRegenerateApiKey,
  useServiceMetrics,
} from '@/hooks/useServices';
import { useServiceMap } from '@/hooks/useServiceMap';
import { useAlertRules, useDeleteAlertRule } from '@/hooks/useAlerts';
import type { MetricsRange } from '@/api/services.api';
import { useOrganizationStore } from '@/stores/useOrganizationStore';

export function ServiceDetails() {
  const { serviceId } = useParams<{ serviceId: string }>();
  const navigate = useNavigate();
  const currentOrgId = useOrganizationStore((state) => state.selectedOrganizationId);

  const { data: service, isLoading } = useService(serviceId);
  const { data: map } = useServiceMap();
  const { data: alertRules } = useAlertRules(currentOrgId || undefined, serviceId);
  const deleteAlertRule = useDeleteAlertRule();

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [repositoryUrl, setRepositoryUrl] = useState('');
  const [region, setRegion] = useState('');
  const [healthCheckUrl, setHealthCheckUrl] = useState('');
  const [sloTargetPercentage, setSloTargetPercentage] = useState(99.9);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [newApiKey, setNewApiKey] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [metricsRange, setMetricsRange] = useState<MetricsRange>('24h');

  const updateService = useUpdateService();
  const deleteService = useDeleteService();
  const regenerateApiKey = useRegenerateApiKey();
  const { data: metrics, isLoading: metricsLoading } = useServiceMetrics(serviceId, metricsRange);

  useEffect(() => {
    if (service) {
      setName(service.name);
      setDescription(service.description || '');
      setRepositoryUrl(service.repositoryUrl || '');
      setRegion(service.region || '');
      setHealthCheckUrl(service.healthCheckUrl || '');
      setSloTargetPercentage(service.sloTargetPercentage);
    }
  }, [service]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!service || name.trim().length < 3) return;

    updateService.mutate({
      serviceId: service.id,
      request: {
        name: name.trim(),
        description: description.trim() || undefined,
        repositoryUrl: repositoryUrl.trim() || undefined,
        region: region.trim() || undefined,
        healthCheckUrl: healthCheckUrl.trim() || undefined,
        sloTargetPercentage,
      },
    });
  };

  const handleDelete = () => {
    if (!service) return;
    deleteService.mutate(service.id, { onSuccess: () => navigate('/services') });
  };

  const handleRegenerate = () => {
    if (!service) return;
    regenerateApiKey.mutate(service.id, {
      onSuccess: (updated) => setNewApiKey(updated.apiKey),
    });
  };

  const handleCopyKey = async () => {
    if (!newApiKey) return;
    await navigator.clipboard.writeText(newApiKey);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDeleteAlertRule = (ruleId: string) => {
    if (!currentOrgId || !serviceId) return;
    deleteAlertRule.mutate({ orgId: currentOrgId, serviceId, ruleId });
  };

  if (isLoading || !service) {
    return <div className="p-8 text-gray-400">Loading service...</div>;
  }

  const dependsOn = (map?.edges || [])
    .filter((e) => e.sourceId === service.id)
    .map((e) => map?.nodes.find((n) => n.id === e.targetId))
    .filter((n): n is NonNullable<typeof n> => !!n);
  const dependedOnBy = (map?.edges || [])
    .filter((e) => e.targetId === service.id)
    .map((e) => map?.nodes.find((n) => n.id === e.sourceId))
    .filter((n): n is NonNullable<typeof n> => !!n);

  return (
    <div className="p-8 space-y-6">
      <div className="flex items-center space-x-4">
        <Link to="/services" className="text-gray-400 hover:text-white transition-colors">
          <ArrowLeft className="h-5 w-5" />
        </Link>
        <h1 className="text-2xl font-semibold text-white">{service.name}</h1>
        <StatusIndicator status={service.status} size="sm" />
      </div>

      <div className="grid grid-cols-3 gap-6">
        <div className="col-span-2 space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Service Details</CardTitle>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="mb-1 block text-sm text-gray-400">Service Name</label>
                  <Input
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    minLength={3}
                    maxLength={255}
                    required
                  />
                </div>
                <div>
                  <label className="mb-1 block text-sm text-gray-400">Description</label>
                  <Input value={description} onChange={(e) => setDescription(e.target.value)} />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="mb-1 block text-sm text-gray-400">Repository URL</label>
                    <Input value={repositoryUrl} onChange={(e) => setRepositoryUrl(e.target.value)} />
                  </div>
                  <div>
                    <label className="mb-1 block text-sm text-gray-400">Region</label>
                    <Input value={region} onChange={(e) => setRegion(e.target.value)} />
                  </div>
                </div>
                <div>
                  <label className="mb-1 block text-sm text-gray-400">Health Check URL</label>
                  <Input
                    type="url"
                    value={healthCheckUrl}
                    onChange={(e) => setHealthCheckUrl(e.target.value)}
                    placeholder="https://api.example.com/health"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-sm text-gray-400">
                    SLO Target (% uptime)
                  </label>
                  <Input
                    type="number"
                    min={0}
                    max={100}
                    step={0.01}
                    value={sloTargetPercentage}
                    onChange={(e) => setSloTargetPercentage(Number(e.target.value))}
                    className="max-w-[10rem]"
                  />
                  <p className="mt-1 text-xs text-gray-500">
                    Sets the error budget used in the SLA report — e.g. 99.9% allows about 43
                    minutes of downtime per 30-day period.
                  </p>
                </div>

                {updateService.isError && (
                  <div className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">
                    {(axios.isAxiosError(updateService.error) && updateService.error.response?.data?.message) ||
                      'Failed to save changes. Please try again.'}
                  </div>
                )}
                {updateService.isSuccess && (
                  <div className="rounded-md border border-emerald-500/20 bg-emerald-500/10 p-2 text-xs text-emerald-400">
                    Saved.
                  </div>
                )}

                <div className="flex justify-end">
                  <Button type="submit" disabled={name.trim().length < 3 || updateService.isPending}>
                    {updateService.isPending ? 'Saving...' : 'Save Changes'}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>Uptime &amp; Latency</CardTitle>
                <div className="flex space-x-1">
                  {(['24h', '7d', '30d'] as MetricsRange[]).map((r) => (
                    <button
                      key={r}
                      type="button"
                      onClick={() => setMetricsRange(r)}
                      className={`rounded-md px-2 py-1 text-xs font-medium transition-colors ${
                        metricsRange === r
                          ? 'bg-charcoal-700 text-white'
                          : 'text-gray-500 hover:text-gray-300'
                      }`}
                    >
                      {r}
                    </button>
                  ))}
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {metricsLoading ? (
                <div className="h-32 animate-pulse rounded-md bg-charcoal-900" />
              ) : !metrics || metrics.pingCount === 0 ? (
                <p className="text-xs text-gray-500">No ping data yet for this window.</p>
              ) : (
                <div className="space-y-3">
                  <div className="flex items-center space-x-4">
                    <Badge
                      variant={
                        metrics.uptimePercentage >= 99
                          ? 'success'
                          : metrics.uptimePercentage >= 95
                            ? 'warning'
                            : 'danger'
                      }
                    >
                      {metrics.uptimePercentage.toFixed(2)}% uptime
                    </Badge>
                    {metrics.avgLatencyMs != null && (
                      <span className="text-xs text-gray-400">
                        avg latency {Math.round(metrics.avgLatencyMs)}ms
                      </span>
                    )}
                    <span className="text-xs text-gray-500">{metrics.pingCount} samples</span>
                  </div>

                  <div className="h-40 w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart
                        data={metrics.dataPoints.map((p) => ({
                          time: new Date(p.timestamp).toLocaleTimeString([], {
                            hour: '2-digit',
                            minute: '2-digit',
                          }),
                          latencyMs: p.latencyMs,
                        }))}
                        margin={{ top: 4, right: 8, left: 0, bottom: 0 }}
                      >
                        <CartesianGrid stroke="#2a2f3a" strokeDasharray="3 3" vertical={false} />
                        <XAxis dataKey="time" tick={{ fill: '#6b7280', fontSize: 10 }} minTickGap={30} />
                        <YAxis tick={{ fill: '#6b7280', fontSize: 10 }} width={36} />
                        <Tooltip
                          contentStyle={{
                            background: '#1a1d24',
                            border: '1px solid #2a2f3a',
                            borderRadius: 6,
                            fontSize: 12,
                          }}
                          labelStyle={{ color: '#9ca3af' }}
                        />
                        <Line
                          type="monotone"
                          dataKey="latencyMs"
                          stroke="#34d399"
                          strokeWidth={2}
                          dot={false}
                          connectNulls
                        />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Dependencies</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-2 gap-6">
                <div>
                  <p className="mb-2 text-xs uppercase tracking-wider text-gray-500">Depends on</p>
                  {dependsOn.length === 0 ? (
                    <p className="text-sm text-gray-500">No dependencies.</p>
                  ) : (
                    <ul className="space-y-1">
                      {dependsOn.map((n) => (
                        <li key={n.id}>
                          <Link
                            to={`/services/${n.id}`}
                            className="text-sm text-gray-300 hover:text-emerald-400"
                          >
                            {n.name}
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
                <div>
                  <p className="mb-2 text-xs uppercase tracking-wider text-gray-500">Depended on by</p>
                  {dependedOnBy.length === 0 ? (
                    <p className="text-sm text-gray-500">Nothing depends on this service.</p>
                  ) : (
                    <ul className="space-y-1">
                      {dependedOnBy.map((n) => (
                        <li key={n.id}>
                          <Link
                            to={`/services/${n.id}`}
                            className="text-sm text-gray-300 hover:text-emerald-400"
                          >
                            {n.name}
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>API Key</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <p className="text-xs text-gray-500">Rotate it if it's ever been exposed.</p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleRegenerate}
                disabled={regenerateApiKey.isPending}
              >
                {regenerateApiKey.isPending ? 'Regenerating...' : 'Regenerate Key'}
              </Button>

              {newApiKey && (
                <>
                  <div className="flex items-center space-x-2 rounded-md border border-charcoal-700 bg-charcoal-900 p-3">
                    <code className="flex-1 truncate text-xs text-emerald-400">{newApiKey}</code>
                    <button
                      type="button"
                      onClick={handleCopyKey}
                      className="rounded-md p-1.5 text-gray-400 transition-colors hover:bg-charcoal-700 hover:text-white"
                      title="Copy API key"
                    >
                      {copied ? <Check className="h-4 w-4 text-emerald-400" /> : <Copy className="h-4 w-4" />}
                    </button>
                  </div>
                  <p className="text-xs text-amber-400">
                    This won't be shown again — copy it now. The old key stops working immediately.
                  </p>
                </>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>Alert Rules</CardTitle>
                <Link to="/alerts" className="text-xs text-emerald-400 hover:text-emerald-300">
                  Manage
                </Link>
              </div>
            </CardHeader>
            <CardContent>
              {!alertRules || alertRules.length === 0 ? (
                <div className="py-4 text-center text-gray-500">
                  <Bell className="mx-auto mb-2 h-6 w-6 text-gray-600" />
                  <p className="text-xs">No alert rules for this service.</p>
                </div>
              ) : (
                <ul className="space-y-2">
                  {alertRules.map((rule) => (
                    <li
                      key={rule.id}
                      className="flex items-center justify-between rounded-md border border-charcoal-700 bg-charcoal-900 p-2"
                    >
                      <div className="text-xs text-gray-300">
                        <p className="font-medium text-gray-200">
                          {rule.condition.type.replace(/_/g, ' ')}
                          {rule.condition.type !== 'STATUS_DOWN' && ` > ${rule.condition.threshold}`}
                        </p>
                        <p className="text-gray-500">{rule.integrationType}</p>
                      </div>
                      <button
                        type="button"
                        onClick={() => handleDeleteAlertRule(rule.id)}
                        className="rounded p-1 text-gray-500 hover:text-rose-400"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>

          <Card className="border-rose-500/20">
            <CardHeader>
              <CardTitle>Danger Zone</CardTitle>
            </CardHeader>
            <CardContent>
              {!confirmingDelete ? (
                <button
                  type="button"
                  onClick={() => setConfirmingDelete(true)}
                  className="text-sm text-rose-400 transition-colors hover:text-rose-300"
                >
                  Delete this service
                </button>
              ) : (
                <div className="space-y-3">
                  <div className="flex items-start space-x-2">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-rose-400" />
                    <p className="text-xs text-rose-300">
                      This permanently deletes <span className="font-medium">{service.name}</span>,
                      its dependency links, alert rules, and ping history. This can't be undone.
                    </p>
                  </div>
                  {deleteService.isError && (
                    <p className="text-xs text-rose-400">
                      {(axios.isAxiosError(deleteService.error) && deleteService.error.response?.data?.message) ||
                        'Failed to delete service.'}
                    </p>
                  )}
                  <div className="flex justify-end space-x-2">
                    <Button type="button" variant="outline" size="sm" onClick={() => setConfirmingDelete(false)}>
                      Cancel
                    </Button>
                    <Button type="button" variant="danger" size="sm" onClick={handleDelete} disabled={deleteService.isPending}>
                      {deleteService.isPending ? 'Deleting...' : 'Delete Permanently'}
                    </Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
