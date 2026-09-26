import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import { format } from 'date-fns';
import { Footprints } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { FollowApi } from '@/api/follow.api';
import type { FoundTrace } from '@/api/traces.api';
import type { LogEntry } from '@/api/logs.api';
import { useOrganizationStore } from '@/stores/useOrganizationStore';
import { LogLine } from '@/features/logs/LogLine';

const RANGES = [
  { minutes: 60, label: 'Last hour' },
  { minutes: 1440, label: 'Last 24 hours' },
  { minutes: 10080, label: 'Last 7 days' },
];

type Event = { time: number; trace?: FoundTrace; log?: LogEntry };

/**
 * Follow a business key (orderId = o-17) through every service that touched it: requests,
 * separate traces where context was lost (queues, batch jobs), and log lines — in time order.
 */
export function FollowView() {
  const orgId = useOrganizationStore((s) => s.selectedOrganizationId);
  const [search, setSearch] = useSearchParams();
  const [key, setKey] = useState(search.get('key') ?? 'orderId');
  const [value, setValue] = useState(search.get('value') ?? '');
  const [searchedAt, setSearchedAt] = useState(() => Date.now());
  const range = Number(search.get('range') ?? 1440);
  const active = search.get('key') && search.get('value') ? { key: search.get('key')!, value: search.get('value')! } : null;

  const { data, isFetching, error } = useQuery({
    queryKey: ['organizations', orgId, 'follow', active, range, searchedAt],
    queryFn: () =>
      FollowApi.follow(orgId!, { ...active!, from: new Date(searchedAt - range * 60_000).toISOString(), to: new Date(searchedAt).toISOString() }),
    enabled: !!orgId && !!active,
    retry: false,
  });

  const events = useMemo((): Event[] => {
    if (!data) return [];
    return [
      ...data.traces.map((trace) => ({ time: new Date(trace.start).getTime(), trace })),
      ...data.logs.map((log) => ({ time: new Date(log.time).getTime(), log })),
    ].sort((a, b) => a.time - b.time);
  }, [data]);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const next = new URLSearchParams(search);
    next.set('key', key.trim());
    next.set('value', value.trim());
    setSearch(next);
    setSearchedAt(Date.now());
  };

  return (
    <div className="flex h-full flex-col">
      <div className="mb-4">
        <h1 className="text-2xl font-semibold tracking-tight text-white">Follow a key</h1>
        <p className="text-sm text-gray-400">
          Everything that touched an order, payment or user id — across services, separate traces and logs. Tag ids in code with{' '}
          <code className="font-mono text-gray-300">tag('orderId', id)</code>.
        </p>
      </div>

      <form onSubmit={submit} className="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-charcoal-700 bg-charcoal-900 p-3">
        <Input aria-label="Key" value={key} onChange={(e) => setKey(e.target.value)} placeholder="orderId" className="w-40 font-mono" />
        <span className="text-gray-500">=</span>
        <Input aria-label="Value" value={value} onChange={(e) => setValue(e.target.value)} placeholder="o-17" className="w-60 font-mono" />
        <select
          aria-label="Time range"
          value={range}
          onChange={(e) => {
            const next = new URLSearchParams(search);
            next.set('range', e.target.value);
            setSearch(next);
            setSearchedAt(Date.now());
          }}
          className="rounded-md border border-charcoal-700 bg-charcoal-900 p-2 text-sm text-white"
        >
          {RANGES.map((r) => (
            <option key={r.minutes} value={r.minutes}>
              {r.label}
            </option>
          ))}
        </select>
        <Button type="submit" size="sm" disabled={!key.trim() || !value.trim()} isLoading={isFetching}>
          <Footprints className="mr-1 h-3.5 w-3.5" /> Follow
        </Button>
      </form>

      {error && (
        <p className="mb-3 rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-sm text-rose-400">
          {(axios.isAxiosError(error) && error.response?.data?.message) || 'Could not follow this key.'}
        </p>
      )}

      {data && (
        <div className="min-h-0 flex-1 space-y-3 overflow-y-auto">
          <div className="flex flex-wrap items-center gap-2 text-sm text-gray-400" data-testid="follow-summary">
            <span>
              <span className="text-white">{data.traces.length}</span> {data.traces.length === 1 ? 'request' : 'requests'} ·{' '}
              <span className="text-white">{data.logs.length}</span> log {data.logs.length === 1 ? 'line' : 'lines'}
              {data.firstSeen && data.lastSeen && (
                <>
                  {' '}
                  · {format(new Date(data.firstSeen), 'MMM d HH:mm:ss')} → {format(new Date(data.lastSeen), 'MMM d HH:mm:ss')}
                </>
              )}
            </span>
            {data.services.map((s) => (
              <span key={s} className="rounded bg-charcoal-800 px-1.5 text-xs text-gray-300">
                {s}
              </span>
            ))}
          </div>
          {data.logsUnavailable && <p className="text-xs text-gray-500">Logs unavailable: {data.logsUnavailable}</p>}
          {events.length === 0 && <p className="text-sm text-gray-500">Nothing touched {data.key} = {data.value} in this time range.</p>}
          <ol className="relative space-y-1 border-l border-charcoal-700 pl-4" data-testid="follow-timeline">
            {events.map((e, i) =>
              e.trace ? (
                <li key={e.trace.traceId} className="relative">
                  <span className={`absolute -left-[21px] top-3 h-2.5 w-2.5 rounded-full ${e.trace.errors ? 'bg-rose-500' : 'bg-emerald-500'}`} />
                  <Link
                    to={`/traces?trace=${e.trace.traceId}`}
                    className="block rounded-md border border-charcoal-700 bg-charcoal-900 px-3 py-2 hover:border-charcoal-600"
                  >
                    <div className="flex items-center gap-2 text-sm">
                      <span className="font-mono text-xs text-gray-500">{format(new Date(e.trace.start), 'HH:mm:ss.SSS')}</span>
                      <span className="text-gray-100">
                        {e.trace.rootService} <span className="font-mono text-xs">{e.trace.rootOperation}</span>
                      </span>
                      <span className="ml-auto font-mono text-xs text-gray-400">{e.trace.durationMs} ms</span>
                      {e.trace.errors > 0 && <span className="rounded-full bg-rose-500/10 px-2 text-xs text-rose-400">{e.trace.errors} err</span>}
                    </div>
                    <div className="mt-1 flex flex-wrap gap-1">
                      {Object.keys(e.trace.services).map((s) => (
                        <span key={s} className="rounded bg-charcoal-800 px-1.5 text-[11px] text-gray-400">
                          {s}
                        </span>
                      ))}
                    </div>
                  </Link>
                </li>
              ) : (
                <li key={`log-${i}`} className="relative list-none">
                  <span className="absolute -left-[19px] top-2 h-1.5 w-1.5 rounded-full bg-gray-500" />
                  <ul className="rounded-md bg-charcoal-900">
                    <LogLine entry={e.log!} />
                  </ul>
                </li>
              ),
            )}
          </ol>
        </div>
      )}
    </div>
  );
}
