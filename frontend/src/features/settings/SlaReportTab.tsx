import { useState } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { useSlaReport } from '@/hooks/useAnalytics';
import type { ServiceSlaDto } from '@/api/analytics.api';

interface SlaReportTabProps {
  orgId: string;
}

const RANGES = [
  { label: '7d', days: 7 },
  { label: '30d', days: 30 },
  { label: '90d', days: 90 },
];

function formatMinutes(minutes: number): string {
  if (minutes < 60) return `${Math.round(minutes)}m`;
  const hours = minutes / 60;
  if (hours < 24) return `${hours.toFixed(1)}h`;
  return `${(hours / 24).toFixed(1)}d`;
}

function burnBarColor(remainingPercentage: number): string {
  if (remainingPercentage < 0) return 'bg-rose-500';
  if (remainingPercentage < 25) return 'bg-amber-400';
  return 'bg-emerald-400';
}

function ServiceBudgetRow({ sla }: { sla: ServiceSlaDto }) {
  const consumedPct =
    sla.errorBudgetMinutesTotal > 0
      ? Math.min(100, (sla.errorBudgetMinutesConsumed / sla.errorBudgetMinutesTotal) * 100)
      : sla.errorBudgetMinutesConsumed > 0
        ? 100
        : 0;
  const overBudget = sla.errorBudgetRemainingPercentage < 0;

  return (
    <div className="rounded-md border border-charcoal-700 bg-charcoal-900 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
        <span className="text-sm font-medium text-white">{sla.serviceName}</span>
        <div className="flex items-center gap-2">
          <Badge variant={sla.uptimePercentage >= sla.sloTargetPercentage ? 'success' : 'danger'}>
            {sla.uptimePercentage.toFixed(3)}% actual
          </Badge>
          <span className="text-xs text-gray-500">target {sla.sloTargetPercentage}%</span>
        </div>
      </div>

      <div className="mb-2 h-2 w-full overflow-hidden rounded-full bg-charcoal-700">
        <div
          className={`h-full rounded-full transition-all ${burnBarColor(sla.errorBudgetRemainingPercentage)}`}
          style={{ width: `${consumedPct}%` }}
        />
      </div>

      <div className="flex flex-wrap justify-between gap-x-4 gap-y-1 text-xs text-gray-500">
        <span>
          Budget: {formatMinutes(sla.errorBudgetMinutesConsumed)} /{' '}
          {formatMinutes(sla.errorBudgetMinutesTotal)}
          {overBudget && <span className="ml-1 text-rose-400">(exceeded)</span>}
        </span>
        <span>{sla.incidentCount} incidents</span>
        <span>MTTR {formatMinutes(sla.mttrMinutes)}</span>
        <span>MTBF {sla.mtbfHours.toFixed(1)}h</span>
      </div>
    </div>
  );
}

export function SlaReportTab({ orgId }: SlaReportTabProps) {
  const [days, setDays] = useState(30);
  const { data: report, isLoading } = useSlaReport(orgId, days);

  return (
    <div className="max-w-3xl space-y-6">
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>SLA &amp; Error Budgets</CardTitle>
            <div className="flex space-x-1">
              {RANGES.map((r) => (
                <button
                  key={r.days}
                  type="button"
                  onClick={() => setDays(r.days)}
                  className={`rounded-md px-2 py-1 text-xs font-medium transition-colors ${
                    days === r.days
                      ? 'bg-charcoal-700 text-white'
                      : 'text-gray-500 hover:text-gray-300'
                  }`}
                >
                  {r.label}
                </button>
              ))}
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-gray-400">Loading SLA report...</p>
          ) : !report || report.serviceSlas.length === 0 ? (
            <p className="text-sm text-gray-500">No services to report on yet.</p>
          ) : (
            <>
              <div className="mb-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
                <div>
                  <p className="text-xs uppercase tracking-wider text-gray-500">Overall Uptime</p>
                  <p className="text-lg font-medium text-white">
                    {report.overallUptimePercentage.toFixed(3)}%
                  </p>
                </div>
                <div>
                  <p className="text-xs uppercase tracking-wider text-gray-500">Incidents</p>
                  <p className="text-lg font-medium text-white">{report.totalIncidents}</p>
                </div>
                <div>
                  <p className="text-xs uppercase tracking-wider text-gray-500">Avg MTTR</p>
                  <p className="text-lg font-medium text-white">{formatMinutes(report.mttrMinutes)}</p>
                </div>
                <div>
                  <p className="text-xs uppercase tracking-wider text-gray-500">Avg MTBF</p>
                  <p className="text-lg font-medium text-white">{report.mtbfHours.toFixed(1)}h</p>
                </div>
              </div>

              <div className="space-y-3">
                {report.serviceSlas.map((sla) => (
                  <ServiceBudgetRow key={sla.serviceId} sla={sla} />
                ))}
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
