import { useState } from 'react';
import { formatDistanceToNow } from 'date-fns';
import { CheckCircle2, Loader2, XCircle } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import type { TestSuite } from '@/api/testing.api';
import { useTestRun, useTestSuite, useTestSuites } from '@/hooks/useTesting';
import { RunView } from './RunView';

function SuiteBadge({ suite }: { suite: TestSuite }) {
  if (suite.status === 'RUNNING') {
    return (
      <Badge variant="info">
        <Loader2 className="mr-1 h-3 w-3 animate-spin" /> running
      </Badge>
    );
  }
  return <Badge variant={suite.status === 'PASSED' ? 'success' : 'danger'}>{suite.status.toLowerCase()}</Badge>;
}

/** Suite results: every case, whether it passed and which checks failed; open a case for its hops. */
export function SuitesPanel({ selectedId, onSelect }: { selectedId: string | null; onSelect: (id: string) => void }) {
  const { data: suites, isLoading } = useTestSuites();
  const { data: suite } = useTestSuite(selectedId);
  const [runId, setRunId] = useState<string | null>(null);
  const { data: run } = useTestRun(runId);

  if (isLoading) return <p className="text-sm text-gray-400">Loading suites…</p>;
  if (!suites?.length) {
    return <p className="rounded-lg border border-charcoal-700 bg-charcoal-900 p-6 text-sm text-gray-400">No suites have run yet. Run a collection, or <code className="font-mono text-gray-200">sdna test run</code> from CI.</p>;
  }

  return (
    <div className="grid min-h-0 gap-4 lg:grid-cols-[320px_1fr]">
      <ul className="space-y-1 overflow-y-auto rounded-lg border border-charcoal-700 bg-charcoal-900 p-2">
        {suites.map((s) => (
          <li key={s.id}>
            <button
              type="button"
              onClick={() => {
                onSelect(s.id);
                setRunId(null);
              }}
              className={`w-full rounded-md px-2 py-2 text-left ${s.id === selectedId ? 'bg-charcoal-800' : 'hover:bg-charcoal-800/60'}`}
            >
              <div className="flex items-center gap-2">
                <span className="truncate text-sm text-white">{s.name}</span>
                <span className="ml-auto">
                  <SuiteBadge suite={s} />
                </span>
              </div>
              <div className="text-xs text-gray-500">
                {s.passed} passed · {s.failed} failed{s.pending ? ` · ${s.pending} pending` : ''}
                {s.environment ? ` · ${s.environment}` : ''} · {formatDistanceToNow(new Date(s.createdAt), { addSuffix: true })}
              </div>
            </button>
          </li>
        ))}
      </ul>

      <div className="min-h-0 space-y-4 overflow-y-auto rounded-lg border border-charcoal-700 bg-charcoal-900 p-4">
        {!suite ? (
          <p className="text-sm text-gray-500">Pick a suite to see its cases.</p>
        ) : (
          <>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-medium text-white">{suite.name}</h2>
              <SuiteBadge suite={suite} />
            </div>
            <ul className="space-y-2" data-testid="suite-cases">
              {(suite.runs ?? []).map((r) => (
                <li key={r.id}>
                  <button
                    type="button"
                    onClick={() => setRunId(r.id === runId ? null : r.id)}
                    className={`w-full rounded-md border px-3 py-2 text-left ${r.id === runId ? 'border-emerald-500/40 bg-charcoal-800' : 'border-charcoal-700 hover:bg-charcoal-800/60'}`}
                  >
                    <div className="flex items-center gap-2 text-sm">
                      {r.passed == null ? (
                        <Loader2 className="h-4 w-4 animate-spin text-gray-400" />
                      ) : r.passed ? (
                        <CheckCircle2 className="h-4 w-4 text-emerald-400" />
                      ) : (
                        <XCircle className="h-4 w-4 text-rose-400" />
                      )}
                      <span className="text-gray-100">{r.caseName}</span>
                      <span className="ml-auto text-xs text-gray-500">{r.status.toLowerCase().replace('_', ' ')}</span>
                    </div>
                    {r.assertionResults
                      ?.filter((a) => !a.passed)
                      .map((a, i) => (
                        <div key={i} className="ml-6 mt-1 text-xs text-rose-300">
                          {a.description}
                          {a.message && <span className="text-gray-500"> — {a.message}</span>}
                        </div>
                      ))}
                  </button>
                </li>
              ))}
            </ul>
            {run && (
              <div className="border-t border-charcoal-700 pt-4">
                <RunView run={run} />
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
