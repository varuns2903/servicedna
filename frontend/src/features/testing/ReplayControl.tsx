import { useMemo, useState } from 'react';
import axios from 'axios';
import { RotateCcw } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { TestRun } from '@/api/testing.api';
import { useEnvironments, useReplay } from '@/hooks/useTesting';

/**
 * Re-sends the request a trace recorded, through a runner in the chosen environment. Production-
 * like environments are listed last and only work where an admin allowed test runs.
 */
export function ReplayControl({ traceId, spanId, onReplayed }: { traceId: string; spanId?: string; onReplayed: (run: TestRun) => void }) {
  const { data: environments } = useEnvironments();
  const replay = useReplay();
  const [error, setError] = useState<string | null>(null);
  const options = useMemo(
    () => [...(environments ?? [])].sort((a, b) => Number(a.productionLike) - Number(b.productionLike) || a.environment.localeCompare(b.environment)),
    [environments],
  );
  const [chosen, setChosen] = useState<string | null>(null);
  const environment = chosen ?? options.find((e) => !e.productionLike || e.allowTestRuns)?.environment ?? null;

  const send = async () => {
    setError(null);
    try {
      onReplayed(await replay.mutateAsync({ traceId, spanId, environment }));
    } catch (err) {
      setError((axios.isAxiosError(err) && err.response?.data?.message) || 'Replay failed.');
    }
  };

  return (
    <div className="space-y-1">
      <div className="flex items-center gap-2">
        <select
          aria-label="Replay environment"
          value={environment ?? ''}
          onChange={(e) => setChosen(e.target.value || null)}
          className="rounded-md border border-charcoal-700 bg-charcoal-900 p-1.5 text-xs text-white"
        >
          {options.map((e) => (
            <option key={e.environment} value={e.environment} disabled={e.productionLike && !e.allowTestRuns}>
              {e.environment}
              {e.productionLike && !e.allowTestRuns ? ' (test runs off)' : ''}
            </option>
          ))}
        </select>
        <Button size="sm" variant="outline" onClick={send} isLoading={replay.isPending} disabled={!environment}>
          <RotateCcw className="mr-1 h-3.5 w-3.5" /> Replay
        </Button>
      </div>
      {error && <p className="text-xs text-rose-400">{error}</p>}
    </div>
  );
}
