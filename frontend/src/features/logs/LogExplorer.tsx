import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import axios from 'axios';
import { Search, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import type { LogSearchParams } from '@/api/logs.api';
import { useServices } from '@/hooks/useServices';
import { useLogSearch } from '@/hooks/useLogs';
import { LogLine } from './LogLine';

const RANGES = [
  { minutes: 15, label: 'Last 15 min' },
  { minutes: 60, label: 'Last hour' },
  { minutes: 360, label: 'Last 6 hours' },
  { minutes: 1440, label: 'Last 24 hours' },
  { minutes: 10080, label: 'Last 7 days' },
];

const field = 'rounded-md border border-charcoal-700 bg-charcoal-900 p-2 text-sm text-white';

function paramsFrom(search: URLSearchParams, now: number): LogSearchParams {
  const minutes = Number(search.get('range') ?? 60);
  return {
    service: search.get('service') || undefined,
    environment: search.get('environment') || undefined,
    level: (search.get('level') as LogSearchParams['level']) || undefined,
    text: search.get('text') || undefined,
    traceId: search.get('traceId') || undefined,
    attribute: search.getAll('attribute'),
    q: search.get('q') || undefined,
    from: new Date(now - minutes * 60_000).toISOString(),
    to: new Date(now).toISOString(),
    limit: 500,
  };
}

/**
 * Kibana-style log search across every service: by service, environment, level, text or any
 * attribute — or LogQL. Lines written during a request link to its trace.
 */
export function LogExplorer() {
  const [search, setSearch] = useSearchParams();
  // Edits go to a draft; Search (or changing the time range) applies it to the URL.
  const [draft, setDraft] = useState(() => new URLSearchParams(search));
  const [searchedAt, setSearchedAt] = useState(() => Date.now());
  const [attributeDraft, setAttributeDraft] = useState('');
  const [showQuery, setShowQuery] = useState(!!search.get('q'));
  const [queryDraft, setQueryDraft] = useState(search.get('q') ?? '');

  const params = useMemo(() => paramsFrom(search, searchedAt), [search, searchedAt]);
  const { data, isFetching, error } = useLogSearch(params);
  const { data: services } = useServices();
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
          <h1 className="text-2xl font-semibold tracking-tight text-white">Logs</h1>
          <p className="text-sm text-gray-400">Every service's logs in one place, each line tied to the request that wrote it.</p>
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
          <select aria-label="Environment" value={draft.get('environment') ?? ''} onChange={(e) => set('environment', e.target.value)} className={field}>
            <option value="">All environments</option>
            {environments.map((env) => (
              <option key={env}>{env}</option>
            ))}
          </select>
          <select aria-label="Level" value={draft.get('level') ?? ''} onChange={(e) => set('level', e.target.value)} className={field}>
            <option value="">Any level</option>
            <option value="error">Errors</option>
            <option value="warn">Warnings</option>
            <option value="info">Info</option>
            <option value="debug">Debug</option>
          </select>
          <Input aria-label="Contains" placeholder="Contains, e.g. o-17" value={draft.get('text') ?? ''} onChange={(e) => set('text', e.target.value)} />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {attributes.map((a) => (
            <span key={a} className="flex items-center gap-1 rounded-full border border-charcoal-600 bg-charcoal-800 px-2 py-0.5 font-mono text-xs text-gray-200">
              {a}
              <button type="button" aria-label={`Remove ${a}`} onClick={() => setAttributes(attributes.filter((x) => x !== a))} className="text-gray-500 hover:text-white">
                <X className="h-3 w-3" />
              </button>
            </span>
          ))}
          <Input
            aria-label="Attribute filter"
            placeholder="Attribute: orderId=o-17"
            value={attributeDraft}
            onChange={(e) => setAttributeDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && attributeDraft.trim()) {
                e.preventDefault();
                if (!attributes.includes(attributeDraft.trim())) setAttributes([...attributes, attributeDraft.trim()]);
                setAttributeDraft('');
              }
            }}
            className="h-8 max-w-xs font-mono text-xs"
          />
          <button type="button" onClick={() => setShowQuery(!showQuery)} className="ml-auto text-xs text-gray-400 hover:text-white">
            {showQuery ? 'Use filters' : 'Write LogQL'}
          </button>
          <Button type="submit" size="sm" isLoading={isFetching}>
            <Search className="mr-1 h-3.5 w-3.5" /> Search
          </Button>
        </div>
        {showQuery ? (
          <textarea
            aria-label="LogQL"
            rows={2}
            value={queryDraft}
            placeholder={data?.query ?? '{service_name="order-service"} |= "failed"'}
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

      {error && (
        <p className="mb-3 rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-sm text-rose-400">
          {(axios.isAxiosError(error) && error.response?.data?.message) || 'Search failed.'}
        </p>
      )}

      <div className="min-h-0 flex-1 overflow-y-auto rounded-lg border border-charcoal-700 bg-charcoal-900">
        {data && (
          <div className="border-b border-charcoal-700 px-3 py-2 text-xs text-gray-500">
            {data.entries.length === 500 ? 'Latest 500 lines' : `${data.entries.length} ${data.entries.length === 1 ? 'line' : 'lines'}`}
          </div>
        )}
        {data?.entries.length === 0 && <p className="p-4 text-sm text-gray-500">No logs matched in this time range.</p>}
        <ul data-testid="log-results">
          {data?.entries.map((entry, i) => (
            <LogLine key={`${entry.time}|${i}`} entry={entry} />
          ))}
        </ul>
      </div>
    </div>
  );
}
