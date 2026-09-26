import { useState } from 'react';
import { CheckCircle2, ChevronDown, ChevronRight, Loader2, Plus, XCircle } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { FINISHED, type Hop, type TestRun } from '@/api/testing.api';
import { prettyJson } from './requests';

const STATUS_TEXT: Record<TestRun['status'], string> = {
  QUEUED: 'Waiting for a runner',
  RUNNING: 'Sending',
  WAITING: 'Collecting spans',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  TIMED_OUT: 'Timed out',
};

function statusVariant(run: TestRun) {
  if (run.status === 'COMPLETED') return 'success' as const;
  if (run.status === 'FAILED' || run.status === 'TIMED_OUT') return 'danger' as const;
  return 'info' as const;
}

/**
 * A run as it happens: the runner's response to the entry call, then every hop through the
 * services as their spans arrive, with what each received and returned.
 */
export function RunView({ run, onAssertHop }: { run: TestRun; onAssertHop?: (hop: Hop) => void }) {
  const finished = FINISHED.includes(run.status);
  // Already in call order (callers first); timings are only for the bars.
  const hops = run.hops ?? [];
  const t0 = Math.min(...hops.map((h) => new Date(h.start).getTime()));
  const total = Math.max(1, ...hops.map((h) => new Date(h.start).getTime() - t0 + h.durationMs));

  return (
    <div className="space-y-4" data-testid="run-view">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <Badge variant={statusVariant(run)}>
          {!finished && <Loader2 className="mr-1 h-3 w-3 animate-spin" />}
          {STATUS_TEXT[run.status]}
        </Badge>
        {run.passed != null && (
          <Badge variant={run.passed ? 'success' : 'danger'}>{run.passed ? 'Assertions passed' : 'Assertions failed'}</Badge>
        )}
        {run.environment && <span className="text-gray-400">in {run.environment}</span>}
        {run.runner && <span className="text-gray-500">via {run.runner}</span>}
        <span className="ml-auto font-mono text-xs text-gray-500" title="Trace ID">
          {run.traceId}
        </span>
      </div>

      {run.error && <p className="rounded-md border border-rose-500/20 bg-rose-500/10 p-3 text-sm text-rose-400">{run.error}</p>}

      {run.result && (
        <section>
          <h3 className="mb-1 text-xs uppercase tracking-wider text-gray-500">Response</h3>
          <div className="rounded-md border border-charcoal-700 bg-charcoal-800 p-3 text-sm">
            <div className="mb-2 flex flex-wrap gap-3 text-gray-300">
              {run.result.status != null && (
                <span>
                  status <span className={`font-mono ${run.result.status >= 400 ? 'text-rose-400' : 'text-emerald-400'}`}>{run.result.status}</span>
                </span>
              )}
              {run.result.durationMs != null && <span>{run.result.durationMs} ms</span>}
              {run.result.partition != null && (
                <span>
                  partition {run.result.partition} · offset {run.result.offset}
                </span>
              )}
            </div>
            {run.result.body && <Code text={prettyJson(run.result.body)} />}
          </div>
        </section>
      )}

      {run.assertionResults && run.assertionResults.length > 0 && (
        <section>
          <h3 className="mb-1 text-xs uppercase tracking-wider text-gray-500">Assertions</h3>
          <ul className="space-y-1 text-sm" data-testid="assertion-results">
            {run.assertionResults.map((r, i) => (
              <li key={i} className="flex items-start gap-2">
                {r.passed ? (
                  <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" />
                ) : (
                  <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-400" />
                )}
                <span className="text-gray-200">
                  {r.description}
                  {!r.passed && r.message && <span className="text-gray-500"> — {r.message}</span>}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section>
        <h3 className="mb-1 text-xs uppercase tracking-wider text-gray-500">
          Hops {hops.length > 0 && <span className="normal-case text-gray-600">({hops.length})</span>}
        </h3>
        {hops.length === 0 ? (
          <p className="text-sm text-gray-500">
            {run.status === 'QUEUED'
              ? 'No runner has picked this up yet. Is a runner running in this environment?'
              : finished
                ? 'No spans arrived for this run.'
                : 'Spans appear here as the services report them…'}
          </p>
        ) : (
          <ul className="space-y-1" data-testid="hops">
            {hops.map((hop) => (
              <HopRow key={hop.spanId} hop={hop} t0={t0} total={total} onAssert={onAssertHop} />
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function HopRow({ hop, t0, total, onAssert }: { hop: Hop; t0: number; total: number; onAssert?: (hop: Hop) => void }) {
  const [open, setOpen] = useState(false);
  const offset = ((new Date(hop.start).getTime() - t0) / total) * 100;
  const width = Math.max(0.5, (hop.durationMs / total) * 100);
  const captured = Object.entries(hop.captured ?? {});
  const hasDetail = hop.requestBody || hop.responseBody || captured.length > 0 || hop.statusMessage;

  return (
    <li className="rounded-md border border-charcoal-700 bg-charcoal-800">
      <div className="flex items-center gap-2 px-2 py-1.5 text-sm">
        <button type="button" onClick={() => setOpen(!open)} className="flex min-w-0 flex-1 items-center gap-2 text-left" disabled={!hasDetail}>
          {hasDetail ? (
            open ? <ChevronDown className="h-4 w-4 shrink-0 text-gray-500" /> : <ChevronRight className="h-4 w-4 shrink-0 text-gray-500" />
          ) : (
            <span className="w-4 shrink-0" />
          )}
          <span className="shrink-0 text-gray-400">{hop.service}</span>
          <span className="truncate font-mono text-xs text-gray-100">{hop.operation}</span>
          {hop.httpStatus != null && (
            <span className={`font-mono text-xs ${hop.httpStatus >= 400 ? 'text-rose-400' : 'text-emerald-400'}`}>{hop.httpStatus}</span>
          )}
          {hop.error && <XCircle className="h-3.5 w-3.5 shrink-0 text-rose-400" />}
        </button>
        <div className="relative hidden h-2 w-40 shrink-0 rounded bg-charcoal-900 sm:block">
          <div
            className={`absolute h-2 rounded ${hop.error ? 'bg-rose-500' : 'bg-emerald-500/70'}`}
            style={{ left: `${Math.min(offset, 99.5)}%`, width: `${width}%` }}
          />
        </div>
        <span className="w-16 shrink-0 text-right font-mono text-xs text-gray-400">{hop.durationMs.toFixed(1)} ms</span>
        {onAssert && (
          <button
            type="button"
            onClick={() => onAssert(hop)}
            title="Assert this hop happens"
            className="rounded p-0.5 text-gray-500 hover:bg-charcoal-700 hover:text-emerald-400"
          >
            <Plus className="h-4 w-4" />
          </button>
        )}
      </div>
      {open && (
        <div className="space-y-2 border-t border-charcoal-700 p-2 text-xs">
          {hop.statusMessage && <p className="text-rose-400">{hop.statusMessage}</p>}
          {hop.requestBody && <Labeled label="Received" text={prettyJson(hop.requestBody)} />}
          {hop.responseBody && <Labeled label="Returned" text={prettyJson(hop.responseBody)} />}
          {captured.length > 0 && (
            <div>
              <div className="mb-1 text-gray-500">Captured</div>
              <dl className="grid grid-cols-[max-content_1fr] gap-x-3 font-mono">
                {captured.map(([k, v]) => (
                  <div key={k} className="contents">
                    <dt className="text-gray-400">{k}</dt>
                    <dd className="break-all text-gray-100">{v}</dd>
                  </div>
                ))}
              </dl>
            </div>
          )}
        </div>
      )}
    </li>
  );
}

function Labeled({ label, text }: { label: string; text: string }) {
  return (
    <div>
      <div className="mb-1 text-gray-500">{label}</div>
      <Code text={text} />
    </div>
  );
}

function Code({ text }: { text: string }) {
  return <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-all rounded bg-charcoal-900 p-2 font-mono text-xs text-gray-200">{text}</pre>;
}
