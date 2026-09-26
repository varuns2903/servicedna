import { useMemo, useState } from 'react';
import type { Trace, TraceSpan } from '@/api/traces.api';

const PALETTE = ['#10b981', '#38bdf8', '#a78bfa', '#f59e0b', '#f472b6', '#34d399', '#60a5fa', '#fb923c'];

/**
 * A trace as a waterfall: one bar per span, indented by depth, coloured by service. Internal spans
 * (middleware, handlers) are hidden by default so the hops between services stand out; their
 * children are re-attached to the nearest visible ancestor.
 */
export function TraceWaterfall({ trace, initialSpanId }: { trace: Trace; initialSpanId?: string }) {
  const [selected, setSelected] = useState<TraceSpan | null>(() => trace.spans.find((s) => s.spanId === initialSpanId) ?? null);
  const [hideInternal, setHideInternal] = useState(true);
  const start = new Date(trace.start).getTime();
  const total = Math.max(trace.durationMs, 1);

  const { rows, colors } = useMemo(() => {
    const byId = new Map(trace.spans.map((s) => [s.spanId, s]));
    const visible = (s: TraceSpan) => !hideInternal || s.kind !== 'INTERNAL' || !s.parentSpanId;
    const visibleParent = (s: TraceSpan): string | null => {
      let parent = s.parentSpanId ? byId.get(s.parentSpanId) : undefined;
      while (parent && !visible(parent)) {
        parent = parent.parentSpanId ? byId.get(parent.parentSpanId) : undefined;
      }
      return parent ? parent.spanId : null;
    };
    const children = new Map<string | null, TraceSpan[]>();
    for (const span of trace.spans.filter(visible)) {
      const parent = visibleParent(span);
      children.set(parent, [...(children.get(parent) ?? []), span]);
    }
    const ordered: { span: TraceSpan; depth: number }[] = [];
    const walk = (parent: string | null, depth: number) => {
      for (const span of children.get(parent) ?? []) {
        ordered.push({ span, depth });
        walk(span.spanId, depth + 1);
      }
    };
    walk(null, 0);
    const services = [...new Set(trace.spans.map((s) => s.service))];
    return { rows: ordered, colors: new Map(services.map((s, i) => [s, PALETTE[i % PALETTE.length]])) };
  }, [trace, hideInternal]);

  const hidden = trace.spans.length - rows.length;
  return (
    <div className="space-y-2">
      <label className="flex items-center gap-1.5 text-[11px] text-gray-400">
        <input type="checkbox" checked={hideInternal} onChange={(e) => setHideInternal(e.target.checked)} />
        Hide internal spans{hideInternal && hidden > 0 ? ` (${hidden} hidden)` : ''}
      </label>
      <div className="space-y-0.5">
        {rows.map(({ span, depth }) => {
          const offset = ((new Date(span.start).getTime() - start) / total) * 100;
          const width = Math.max((span.durationMs / total) * 100, 0.5);
          return (
            <button
              key={span.spanId}
              type="button"
              onClick={() => setSelected(span)}
              className={`flex w-full items-center gap-2 rounded px-1 py-0.5 text-left text-[11px] hover:bg-charcoal-800 ${selected?.spanId === span.spanId ? 'bg-charcoal-800' : ''}`}
            >
              <div className="w-40 shrink-0 truncate" style={{ paddingLeft: depth * 8 }} title={`${span.service} · ${span.name}`}>
                <span style={{ color: colors.get(span.service) }}>{span.service}</span>{' '}
                <span className="text-gray-300">{span.name}</span>
              </div>
              <div className="relative h-3 flex-1 rounded-sm bg-charcoal-800">
                <div
                  className="absolute h-3 rounded-sm"
                  style={{ left: `${offset}%`, width: `${width}%`, background: span.error ? '#f43f5e' : colors.get(span.service) }}
                />
              </div>
              <div className="w-14 shrink-0 text-right font-mono text-gray-400">{span.durationMs}ms</div>
            </button>
          );
        })}
      </div>
      {selected && <SpanDetails span={selected} />}
    </div>
  );
}

const BODIES = ['sdna.request.body', 'sdna.response.body'];

function pretty(text: string) {
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

function SpanDetails({ span }: { span: TraceSpan }) {
  const bodies = BODIES.filter((k) => span.attributes[k]);
  return (
    <div className="rounded-md border border-charcoal-700 bg-charcoal-800 p-2 text-[11px]">
      <div className="mb-1 flex items-center gap-2">
        <span className="font-mono text-gray-100">{span.name}</span>
        <span className="text-gray-500">
          {span.kind.toLowerCase()} · {span.service}
        </span>
        {span.error && <span className="text-rose-400">error{span.statusMessage ? `: ${span.statusMessage}` : ''}</span>}
      </div>
      {bodies.length > 0 && (
        <div className="mb-2 space-y-1">
          {span.attributes['sdna.captured_on_error'] && <div className="text-amber-300">Bodies captured because this request failed</div>}
          {bodies.map((k) => (
            <div key={k}>
              <div className="text-gray-500">{k === 'sdna.request.body' ? 'Received' : 'Returned'}</div>
              <pre className="max-h-48 overflow-auto whitespace-pre-wrap break-all rounded bg-charcoal-900 p-1.5 font-mono text-gray-200">{pretty(span.attributes[k])}</pre>
            </div>
          ))}
        </div>
      )}
      <table className="w-full">
        <tbody>
          {Object.entries(span.attributes).filter(([k]) => !BODIES.includes(k)).map(([k, v]) => (
            <tr key={k} className="align-top">
              <td className="w-1/3 pr-2 text-gray-500">{k}</td>
              <td className="break-all font-mono text-gray-200">{v}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {span.events.length > 0 && (
        <div className="mt-1 border-t border-charcoal-700 pt-1">
          {span.events.map((e, i) => (
            <div key={i} className="text-gray-300">
              <span className="text-gray-500">event</span> {e.name}{' '}
              {Object.entries(e.attributes).map(([k, v]) => `${k}=${v}`).join(' ')}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
