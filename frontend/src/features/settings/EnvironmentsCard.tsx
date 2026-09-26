import { useState } from 'react';
import axios from 'axios';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { useEnvironments, useUpdateEnvironment } from '@/hooks/useTesting';

/**
 * Environments the organization's services report. Test runs are allowed everywhere except
 * production-like environments, where an admin has to switch them on.
 */
export function EnvironmentsCard({ orgId }: { orgId: string }) {
  const { data: environments } = useEnvironments(orgId);
  const update = useUpdateEnvironment(orgId);
  const [error, setError] = useState<string | null>(null);

  const toggle = (environment: string, allowTestRuns: boolean) => {
    setError(null);
    update.mutate(
      { environment, allowTestRuns },
      { onError: (err) => setError((axios.isAxiosError(err) && err.response?.data?.message) || 'Could not update the environment.') },
    );
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Environments</CardTitle>
        <p className="text-sm text-gray-400">
          Where Test Studio and <code className="font-mono">sdna test run</code> may send requests. Production-like environments are off until you allow them;
          every run is audit-logged.
        </p>
      </CardHeader>
      <CardContent>
        {error && <p className="mb-3 text-sm text-rose-400">{error}</p>}
        {environments?.length ? (
          <ul className="divide-y divide-charcoal-700" data-testid="environments">
            {environments.map((e) => (
              <li key={e.environment} className="flex items-center gap-3 py-2 text-sm">
                <span className="font-mono text-gray-100">{e.environment}</span>
                {e.productionLike && <Badge variant="warning">production-like</Badge>}
                <label className="ml-auto flex items-center gap-2 text-gray-300">
                  <input
                    type="checkbox"
                    aria-label={`Allow test runs in ${e.environment}`}
                    checked={e.allowTestRuns}
                    disabled={update.isPending}
                    onChange={(ev) => toggle(e.environment, ev.target.checked)}
                  />
                  Allow test runs
                </label>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-gray-500">No environments yet — they appear once services report deployment.environment.</p>
        )}
      </CardContent>
    </Card>
  );
}
