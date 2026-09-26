import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import axios from 'axios';
import { format } from 'date-fns';
import { Search, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import type { FoundTrace, TraceExploreParams } from '@/api/traces.api';
import { useServices } from '@/hooks/useServices';
import { useTrace, useTraceAttributes, useTraceExplore } from '@/hooks/useTraces';
import { TraceWaterfall } from './TraceWaterfall';
import { TraceLogs } from '@/features/logs/TraceLogs';

const RANGES = [
  { minutes: 15, label: 'Last 15 min' },
  { minutes: 60, label: 'Last hour' },
  { minutes: 360, label: 'Last 6 hours' },
  { minutes: 1440, label: 'Last 24 hours' },
  { minutes: 10080, label: 'Last 7 days' },
];

const field = 'rounded-md border border-charcoal-700 bg-charcoal-900 p-2 text-sm text-white';

/** The URL is the search: filters are shareable and survive a reload. */
function paramsFrom(search: URLSearchParams, now: number): TraceExploreParams {
  const minutes = Number(search.get('range') ?? 60);
  const num = (key: string) => (search.get(key) ? Number(search.get(key)) : undefined);
  return {
    service: search.get('service') || undefined,
    operation: search.get('operation') || undefined,
    environment: search.get('environment') || undefined,
    status: (search.get('status') as 'error' | 'ok' | null) || undefined,
    minDurationMs: num('minDurationMs'),
    maxDurationMs: num('maxDurationMs'),
    attribute: search.getAll('attribute'),
    text: search.get('text') || undefined,
    q: search.get('q') || undefined,
    from: new Date(now - minutes * 60_000).toISOString(),
    to: new Date(now).toISOString(),
    limit: 50,
  };
}

function errorMessage(err: unknown) {
  return (axios.isAxiosError(err) && err.response?.data?.message) || 'Search failed.';
}

/**
 * Kibana-style trace search: filter by service, operation, environment, status, latency, any
 * attribute (business keys like orderId included) or text inside captured bodies — or write
 * TraceQL. Open a result for its waterfall, starting at the span that matched.
 */
export function TraceExplorer() {
  const [search, setSearch] = useSearchParams();
  // Edits go to a draft; Search (or changing the time range) applies it to the URL.
  const [draft, setDraft] = useState(() => new URLSearchParams(search));
  const [searchedAt, setSearchedAt] = useState(() => Date.now());
  const [attributeDraft, setAttributeDraft] = useState('');
  const [showQuery, setShowQuery] = useState(!!search.get('q'));
  const [queryDraft, setQueryDraft] = useState(search.get('q') ?? '');
  const [open, setOpen] = useState<{ traceId: string; spanId?: string } | null>(() => (search.get('trace') ? { traceId: search.get('trace')! } : null));

  const params = useMemo(() => paramsFrom(search, searchedAt), [search, searchedAt]);
  const { data, isFetching, error } = useTraceExplore(params);
  const { data: services } = useServices();
  const { data: attributeNames } = useTraceAttributes();

  const serviceNames = useMemo(() => [...new Set((services ?? []).map((s) => s.name))].sort(), [services]);
  const environments = useMemo(
    () => [...new Set((services ?? []).map((s) => s.environment).filter((e): e is string => !!e))].sort(),
    [services],
  );

  const set = (key: string, value: string | null) =>
    setDraft((d) => {
      const next = new URLSearchParams(d);
      if (value) next.set(key, value);
      else next.delete(key);
      return next;
    });
  const attributes = draft.getAll('attribute');
  const setAttributes = (values: string[]) =>
    setDraft((d) => {
      const next = new URLSearchParams(d);
      next.delete('attribute');
      values.forEach((v) => next.append('attribute', v));
      return next;
    });
  const addAttribute = () => {
    const value = attributeDraft.trim();
    if (value && !attributes.includes(value)) setAttributes([...attributes, value]);
    setAttributeDraft('');
  };

  const apply = (next: URLSearchParams) => {
    setDraft(next);
    setSearch(next);
    setSearchedAt(Date.now());
  };

  const submit = (e?: React.FormEvent) => {
    e?.preventDefault();
    const next = new URLSearchParams(draft);
    const pending = attributeDraft.trim();
    if (pending && !next.getAll('attribute').includes(pending)) next.append('attribute', pending);
    setAttributeDraft('');
    if (showQuery && queryDraft.trim()) next.set('q', queryDraft.trim());
    else next.delete('q');
    apply(next);
  };

  return (
    <div className="flex h-full flex-col">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-white">Traces</h1>
          <p className="text-sm text-gray-400">Search every request across your services — by what happened, or by the data it carried.</p>
        </div>
        <select
          aria-label="Time range"
          value={search.get('range') ?? '60'}
          onChange={(e) => {
            const next = new URLSearchParams(draft);
            next.set('range', e.target.value);
            apply(next);
          }}
          className={field}
        >
          {RANGES.map((r) => (
            <option key={r.minutes} value={r.minutes}>
              {r.label}
            </option>
          ))}
        </select>
      </div>

      <form onSubmit={submit} className="mb-4 space-y-3 rounded-lg border border-charcoal-700 bg-charcoal-900 p-3">
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
          <select aria-label="Service" value={draft.get('service') ?? ''} onChange={(e) => set('service', e.target.value)} className={field}>
            <option value="">All services</option>
            {serviceNames.map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
          <Input aria-label="Operation" placeholder="Operation, e.g. POST /orders" value={draft.get('operation') ?? ''} onChange={(e) => set('operation', e.target.value)} />
          <select aria-label="Environment" value={draft.get('environment') ?? ''} onChange={(e) => set('environment', e.target.value)} className={field}>
            <option value="">All environments</option>
            {environments.map((env) => (
              <option key={env}>{env}</option>
            ))}
          </select>
          <select aria-label="Status" value={draft.get('status') ?? ''} onChange={(e) => set('status', e.target.value)} className={field}>
            <option value="">Any status</option>
            <option value="error">Errors</option>
            <option value="ok">Not errors</option>
          </select>
          <Input aria-label="Min duration" type="number" min={0} placeholder="Slower than (ms)" value={draft.get('minDurationMs') ?? ''} onChange={(e) => set('minDurationMs', e.target.value)} />
          <Input aria-label="Max duration" type="number" min={0} placeholder="Faster than (ms)" value={draft.get('maxDurationMs') ?? ''} onChange={(e) => set('maxDurationMs', e.target.value)} />
          <Input
            aria-label="Body contains"
            placeholder="Body contains, e.g. o-17"
            value={draft.get('text') ?? ''}
            onChange={(e) => set('text', e.target.value)}
            className="lg:col-span-2"
          />
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {attributes.map((a) => (
            <span key={a} className="flex items-center gap-1 rounded-full border border-charcoal-600 bg-charcoal-800 px-2 py-0.5 font-mono text-xs text-gray-200" data-testid="attribute-chip">
              {a}
              <button type="button" aria-label={`Remove ${a}`} onClick={() => setAttributes(attributes.filter((x) => x !== a))} className="text-gray-500 hover:text-white">
                <X className="h-3 w-3" />
              </button>
            </span>
          ))}
          <Input
            aria-label="Attribute filter"
            list="trace-attributes"
            placeholder="Attribute: orderId=o-17, http.response.status_code>=500"
            value={attributeDraft}
            onChange={(e) => setAttributeDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && attributeDraft.trim()) {
                e.preventDefault();
                addAttribute();
              }
            }}
            className="h-8 max-w-sm font-mono text-xs"
          />
          <datalist id="trace-attributes">
            {(attributeNames ?? []).map((n) => (
              <option key={n} value={`${n}=`} />
            ))}
          </datalist>
          <button type="button" onClick={() => setShowQuery(!showQuery)} className="ml-auto text-xs text-gray-400 hover:text-white">
            {showQuery ? 'Use filters' : 'Write TraceQL'}
          </button>
          <Button type="submit" size="sm" isLoading={isFetching}>
            <Search className="mr-1 h-3.5 w-3.5" /> Search
          </Button>
        </div>

        {showQuery ? (
          <textarea
            aria-label="TraceQL"
            rows={2}
            value={queryDraft}
            placeholder={data?.query ?? '{ status = error }'}
            onChange={(e) => setQueryDraft(e.target.value)}
            className={`${field} w-full font-mono text-xs`}
          />
        ) : (
          data?.query && (
            <p className="truncate font-mono text-[11px] text-gray-500" title={data.query}>
              {data.query}
            </p>
          )
        )}
      </form>

      {error && <p className="mb-3 rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-sm text-rose-400">{errorMessage(error)}</p>}

      <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-2">
        <div className="min-h-0 overflow-y-auto rounded-lg border border-charcoal-700 bg-charcoal-900">
          {data && (
            <div className="border-b border-charcoal-700 px-3 py-2 text-xs text-gray-500">
              {data.traces.length === 50 ? 'Latest 50 traces' : `${data.traces.length} ${data.traces.length === 1 ? 'trace' : 'traces'}`}
            </div>
          )}
          {data?.traces.length === 0 && <p className="p-4 text-sm text-gray-500">Nothing matched in this time range.</p>}
          <ul data-testid="trace-results">
            {data?.traces.map((t) => (
              <ResultRow
                key={t.traceId}
                trace={t}
                active={open?.traceId === t.traceId}
                onOpen={(spanId) => setOpen({ traceId: t.traceId, spanId })}
              />
            ))}
          </ul>
        </div>
        <div className="min-h-0 overflow-y-auto rounded-lg border border-charcoal-700 bg-charcoal-900 p-3">
          {open ? <OpenTrace traceId={open.traceId} spanId={open.spanId} /> : <p className="text-sm text-gray-500">Open a trace to see every span.</p>}
        </div>
      </div>
    </div>
  );
}

function ResultRow({ trace, active, onOpen }: { trace: FoundTrace; active: boolean; onOpen: (spanId?: string) => void }) {
  const span = trace.spans[0];
  const body = span?.attributes['sdna.request.body'] ?? span?.attributes['sdna.response.body'];
  return (
    <li className={`border-b border-charcoal-800 ${active ? 'bg-charcoal-800' : 'hover:bg-charcoal-800/50'}`}>
      <button type="button" onClick={() => onOpen(span?.spanId)} className="w-full px-3 py-2 text-left">
        <div className="flex items-center gap-2 text-sm">
          <span className="font-mono text-xs text-gray-500">{format(new Date(trace.start), 'MMM d HH:mm:ss')}</span>
          <span className="truncate text-gray-100">
            {trace.rootService} <span className="font-mono text-xs">{trace.rootOperation}</span>
          </span>
          <span className="ml-auto shrink-0 font-mono text-xs text-gray-400">{trace.durationMs} ms</span>
          {trace.errors > 0 && <span className="shrink-0 rounded-full bg-rose-500/10 px-2 text-xs text-rose-400">{trace.errors} err</span>}
        </div>
        <div className="mt-1 flex flex-wrap gap-1">
          {Object.entries(trace.services).map(([name, stats]) => (
            <span key={name} className={`rounded px-1.5 text-[11px] ${stats.errors ? 'bg-rose-500/10 text-rose-300' : 'bg-charcoal-800 text-gray-400'}`}>
              {name}
            </span>
          ))}
        </div>
        {span && (
          <div className="mt-1 truncate text-[11px] text-gray-500">
            matched {span.service} <span className="font-mono">{span.name}</span> · {span.durationMs} ms
            {trace.matched > 1 ? ` · +${trace.matched - 1} more` : ''}
            {body && <span className="ml-2 font-mono text-gray-400">{body}</span>}
          </div>
        )}
      </button>
    </li>
  );
}

function OpenTrace({ traceId, spanId }: { traceId: string; spanId?: string }) {
  const { data: trace, isLoading, error } = useTrace(traceId);
  if (isLoading) return <p className="text-sm text-gray-400">Loading trace…</p>;
  if (error || !trace) return <p className="text-sm text-rose-400">{errorMessage(error)}</p>;
  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 text-sm">
        <span className="font-mono text-xs text-gray-400">{trace.traceId}</span>
        <span className="text-gray-500">
          · {trace.spans.length} spans · {trace.durationMs} ms
        </span>
      </div>
      <TraceWaterfall key={`${traceId}|${spanId}`} trace={trace} initialSpanId={spanId} />
      <TraceLogs traceId={trace.traceId} start={trace.start} />
    </div>
  );
}
