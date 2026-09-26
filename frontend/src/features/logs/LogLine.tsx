import { useState } from 'react';
import { Link } from 'react-router-dom';
import { format } from 'date-fns';
import type { LogEntry } from '@/api/logs.api';

const LEVEL_STYLE: Record<string, string> = {
  error: 'text-rose-400',
  fatal: 'text-rose-400',
  critical: 'text-rose-400',
  warn: 'text-amber-300',
  warning: 'text-amber-300',
  info: 'text-sky-300',
  debug: 'text-gray-500',
};

/** One log line: time, level, service and message; click for its attributes and trace. */
export function LogLine({ entry, showTraceLink = true }: { entry: LogEntry; showTraceLink?: boolean }) {
  const [open, setOpen] = useState(false);
  const level = entry.level ?? '';
  return (
    <li className="border-b border-charcoal-800 font-mono text-xs">
      <button type="button" onClick={() => setOpen(!open)} className="flex w-full gap-2 px-2 py-1 text-left hover:bg-charcoal-800/60">
        <span className="shrink-0 text-gray-500">{format(new Date(entry.time), 'HH:mm:ss.SSS')}</span>
        <span className={`w-10 shrink-0 uppercase ${LEVEL_STYLE[level] ?? 'text-gray-400'}`}>{level.slice(0, 5) || '—'}</span>
        <span className="w-32 shrink-0 truncate text-gray-400">{entry.service}</span>
        <span className="min-w-0 flex-1 whitespace-pre-wrap break-all text-gray-100">{entry.body}</span>
      </button>
      {open && (
        <div className="space-y-1 bg-charcoal-800/40 px-2 py-1.5 pl-[7.5rem]">
          {entry.environment && <div className="text-gray-500">environment <span className="text-gray-300">{entry.environment}</span></div>}
          {Object.entries(entry.attributes).map(([k, v]) => (
            <div key={k} className="text-gray-500">
              {k} <span className="break-all text-gray-300">{v}</span>
            </div>
          ))}
          {entry.traceId && (
            <div className="text-gray-500">
              trace{' '}
              {showTraceLink ? (
                <Link to={`/traces?trace=${entry.traceId}`} className="text-emerald-400 hover:underline">
                  {entry.traceId}
                </Link>
              ) : (
                <span className="text-gray-300">{entry.traceId}</span>
              )}
            </div>
          )}
        </div>
      )}
    </li>
  );
}
