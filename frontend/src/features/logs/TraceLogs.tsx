import { useTraceLogs } from '@/hooks/useLogs';
import { LogLine } from './LogLine';

/** The logs every service wrote while handling one trace, oldest first. */
export function TraceLogs({ traceId, start, live = false }: { traceId: string; start: string; live?: boolean }) {
  const { data, isLoading, error } = useTraceLogs(traceId, start, live ? 3000 : false);
  const entries = [...(data?.entries ?? [])].reverse();
  return (
    <section data-testid="trace-logs">
      <h3 className="mb-1 text-xs uppercase tracking-wider text-gray-500">
        Logs {entries.length > 0 && <span className="normal-case text-gray-600">({entries.length})</span>}
      </h3>
      {isLoading ? (
        <p className="text-xs text-gray-500">Loading logs…</p>
      ) : error ? (
        <p className="text-xs text-gray-500">Logs aren't available.</p>
      ) : entries.length === 0 ? (
        <p className="text-xs text-gray-500">No logs were written during this request.</p>
      ) : (
        <ul className="rounded-md border border-charcoal-700 bg-charcoal-900">
          {entries.map((e, i) => (
            <LogLine key={`${e.time}|${i}`} entry={e} showTraceLink={false} />
          ))}
        </ul>
      )}
    </section>
  );
}
