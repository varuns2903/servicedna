import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { format } from 'date-fns';
import { Paperclip, Trash2, XCircle } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { IncidentsApi, type AttachedTrace } from '@/api/incidents.api';
import type { TestRun } from '@/api/testing.api';
import { useTestRun } from '@/hooks/useTesting';
import { ReplayControl } from '@/features/testing/ReplayControl';
import { RunView } from '@/features/testing/RunView';

/**
 * The requests behind an incident: failing traces through its affected services while it was
 * open, and the ones attached as evidence — kept with their failure path, and replayable.
 */
export function IncidentTraces({ orgId, incidentId }: { orgId: string; incidentId: string }) {
  const queryClient = useQueryClient();
  const failing = useQuery({
    queryKey: ['organizations', orgId, 'incidents', incidentId, 'failing-traces'],
    queryFn: () => IncidentsApi.failingTraces(orgId, incidentId),
    retry: false,
  });
  const attached = useQuery({
    queryKey: ['organizations', orgId, 'incidents', incidentId, 'traces'],
    queryFn: () => IncidentsApi.attachedTraces(orgId, incidentId),
  });
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'incidents', incidentId, 'traces'] });
    queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'incidents', incidentId, 'events'] });
  };
  const attach = useMutation({
    mutationFn: ({ traceId, note }: { traceId: string; note?: string }) => IncidentsApi.attachTrace(orgId, incidentId, traceId, note),
    onSuccess: refresh,
  });
  const detach = useMutation({ mutationFn: (traceId: string) => IncidentsApi.detachTrace(orgId, incidentId, traceId), onSuccess: refresh });
  const [notes, setNotes] = useState<Record<string, string>>({});

  const attachedIds = new Set((attached.data ?? []).map((t) => t.traceId));
  const candidates = (failing.data?.traces ?? []).filter((t) => !attachedIds.has(t.traceId));

  return (
    <Card>
      <CardHeader>
        <CardTitle>Traces</CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">
        <section>
          <h3 className="mb-2 text-xs uppercase tracking-wider text-gray-500">Evidence</h3>
          {attached.data?.length ? (
            <ul className="space-y-3" data-testid="attached-traces">
              {attached.data.map((t) => (
                <AttachedCard key={t.id} trace={t} onDetach={() => detach.mutate(t.traceId)} />
              ))}
            </ul>
          ) : (
            <p className="text-sm text-gray-500">Attach a failing request below; its failure path stays with the incident.</p>
          )}
        </section>

        <section>
          <h3 className="mb-2 text-xs uppercase tracking-wider text-gray-500">Failing requests while this incident was open</h3>
          {failing.isLoading ? (
            <p className="text-sm text-gray-500">Looking for failures…</p>
          ) : failing.error ? (
            <p className="text-sm text-gray-500">
              {(axios.isAxiosError(failing.error) && failing.error.response?.data?.message) || 'Traces are unavailable.'}
            </p>
          ) : candidates.length === 0 ? (
            <p className="text-sm text-gray-500">No failing requests through the affected services in this window.</p>
          ) : (
            <ul className="divide-y divide-charcoal-800 rounded-md border border-charcoal-700" data-testid="failing-traces">
              {candidates.slice(0, 20).map((t) => (
                <li key={t.traceId} className="flex flex-wrap items-center gap-2 px-3 py-2 text-sm">
                  <span className="font-mono text-xs text-gray-500">{format(new Date(t.start), 'HH:mm:ss')}</span>
                  <Link to={`/traces?trace=${t.traceId}`} className="truncate text-gray-100 hover:underline">
                    {t.rootService} <span className="font-mono text-xs">{t.rootOperation}</span>
                  </Link>
                  <span className="text-xs text-rose-400">{t.errors} err</span>
                  <Input
                    aria-label={`Note for ${t.traceId}`}
                    placeholder="Note (optional)"
                    value={notes[t.traceId] ?? ''}
                    onChange={(e) => setNotes({ ...notes, [t.traceId]: e.target.value })}
                    className="ml-auto h-8 w-48 text-xs"
                  />
                  <Button size="sm" variant="outline" onClick={() => attach.mutate({ traceId: t.traceId, note: notes[t.traceId] })} isLoading={attach.isPending && attach.variables?.traceId === t.traceId}>
                    <Paperclip className="mr-1 h-3.5 w-3.5" /> Attach
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </section>
      </CardContent>
    </Card>
  );
}

function AttachedCard({ trace, onDetach }: { trace: AttachedTrace; onDetach: () => void }) {
  const [run, setRun] = useState<TestRun | null>(null);
  const { data: live } = useTestRun(run?.id ?? null);
  const masked = trace.hops.some((h) => h.requestBody?.includes('[masked]'));
  return (
    <li className="rounded-md border border-charcoal-700 bg-charcoal-900 p-3">
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <Link to={`/traces?trace=${trace.traceId}`} className="text-sm text-gray-100 hover:underline">
            {trace.summary}
          </Link>
          <div className="text-xs text-gray-500">
            {trace.attachedBy ?? 'someone'} · {format(new Date(trace.attachedAt), 'MMM d HH:mm')}
            {trace.note && <span className="text-gray-300"> — {trace.note}</span>}
          </div>
        </div>
        <button type="button" aria-label="Detach trace" onClick={onDetach} className="text-gray-500 hover:text-rose-400">
          <Trash2 className="h-4 w-4" />
        </button>
      </div>
      <ol className="mt-2 space-y-0.5 font-mono text-xs">
        {trace.hops.map((h) => {
          const failed = h.error || (h.httpStatus != null && h.httpStatus >= 500);
          return (
            <li key={h.spanId} className={`flex items-center gap-2 ${failed ? 'text-rose-300' : 'text-gray-400'}`}>
              {failed ? <XCircle className="h-3 w-3 shrink-0" /> : <span className="w-3 shrink-0" />}
              <span>{h.service}</span>
              <span className="truncate text-gray-200">{h.operation}</span>
              {h.httpStatus != null && <span>{h.httpStatus}</span>}
              {h.statusMessage && <span className="truncate">{h.statusMessage}</span>}
            </li>
          );
        })}
      </ol>
      <div className="mt-3 border-t border-charcoal-800 pt-2">
        <ReplayControl traceId={trace.traceId} onReplayed={setRun} />
        {masked && <p className="mt-1 text-xs text-amber-300">Masked fields are sent as "[masked]".</p>}
        {live && (
          <div className="mt-3">
            <RunView run={live} />
          </div>
        )}
      </div>
    </li>
  );
}
