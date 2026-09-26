import { useState } from 'react';
import { formatDistanceToNow } from 'date-fns';
import { ArrowLeft } from 'lucide-react';
import type { FlowOperation } from '@/api/flows.api';
import { useTrace, useTraceSearch } from '@/hooks/useTraces';
import { TraceWaterfall } from '@/features/traces/TraceWaterfall';

/** Recent traces where the source operation called the target, and a waterfall of the one picked. */
export function CallTraces({ source, target, windowMinutes }: { source: FlowOperation; target: FlowOperation; windowMinutes: number }) {
  const [errorsOnly, setErrorsOnly] = useState(false);
  const [traceId, setTraceId] = useState<string | null>(null);
  const callee = target.kind === 'SERVICE' ? { calleeService: target.nodeName, calleeOperation: target.operation } : {};
  const { data: traces, isLoading, isError, error } = useTraceSearch({
    service: source.nodeName,
    operation: source.operation || undefined,
    ...callee,
    errorsOnly,
    windowMinutes,
    limit: 20,
  });
  const { data: trace, isLoading: loadingTrace } = useTrace(traceId);

  if (traceId) {
    return (
      <div>
        <button type="button" onClick={() => setTraceId(null)} className="mb-2 flex items-center gap-1 text-xs text-gray-400 hover:text-white">
          <ArrowLeft className="h-3 w-3" /> Traces
        </button>
        <p className="mb-2 font-mono text-[11px] text-gray-500">{traceId}</p>
        {loadingTrace || !trace ? <p className="text-sm text-gray-400">Loading trace…</p> : <TraceWaterfall trace={trace} />}
      </div>
    );
  }

  return (
    <div>
      <div className="mb-2 flex items-center justify-between">
        <h3 className="text-sm font-medium text-gray-200">Recent traces</h3>
        <label className="flex items-center gap-1.5 text-xs text-gray-400">
          <input type="checkbox" checked={errorsOnly} onChange={(e) => setErrorsOnly(e.target.checked)} />
          errors only
        </label>
      </div>
      {isLoading ? (
        <p className="text-sm text-gray-400">Searching…</p>
      ) : isError ? (
        <p className="text-sm text-rose-400">{(error as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Trace search failed.'}</p>
      ) : !traces || traces.length === 0 ? (
        <p className="text-sm text-gray-500">No traces in this window.</p>
      ) : (
        <ul className="space-y-1">
          {traces.map((t) => (
            <li key={t.traceId}>
              <button
                type="button"
                onClick={() => setTraceId(t.traceId)}
                className="flex w-full items-center justify-between rounded-md border border-charcoal-700 px-2 py-1.5 text-left text-xs hover:border-charcoal-600"
              >
                <span className="truncate text-gray-300">
                  {t.rootService} <span className="font-mono text-gray-100">{t.rootOperation}</span>
                </span>
                <span className="shrink-0 pl-2 text-gray-500">
                  {t.durationMs}ms · {formatDistanceToNow(new Date(t.start), { addSuffix: true })}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
