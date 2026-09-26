import { useMemo, useState } from 'react';
import axios from 'axios';
import { Save, Send, Wand2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import type { Assertion, CatalogOperation, Hop, TestCase, TestProtocol, TestRequest } from '@/api/testing.api';
import { useServices } from '@/hooks/useServices';
import { useSaveTestCollection, useSendTestRun, useServiceOperations, useStartTestSuite, useTestCollections, useTestRun } from '@/hooks/useTesting';
import { assertionsFromRun, cleanRequest, formatHeaders, hopAssertion, parseHeaders, requestFor, templateFor } from './requests';
import { RunView } from './RunView';

const PROTOCOLS: { value: TestProtocol; label: string }[] = [
  { value: 'HTTP', label: 'HTTP' },
  { value: 'GRAPHQL', label: 'GraphQL' },
  { value: 'GRPC', label: 'gRPC' },
  { value: 'MESSAGING', label: 'Kafka' },
];
const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'];
const GRAPHQL_TEMPLATE = JSON.stringify({ query: '{ __typename }', variables: {} }, null, 2);

const field = 'w-full rounded-md border border-charcoal-700 bg-charcoal-900 p-2 text-sm text-white';
const mono = `${field} font-mono text-xs`;

export interface ComposerSeed {
  testCase: TestCase;
  collectionId: string | null;
}

function errorMessage(err: unknown, fallback: string) {
  return (axios.isAxiosError(err) && err.response?.data?.message) || fallback;
}

/**
 * Build a request against a service or topic, send it through the environment's runner and watch
 * it travel; turn what happened into assertions and save it as a case in a collection.
 */
export function Composer({ environment, seed }: { environment: string | null; seed?: ComposerSeed }) {
  const initial = seed?.testCase.request;
  const [request, setRequest] = useState<TestRequest>(
    initial ?? { protocol: 'HTTP', method: 'POST', path: '', body: '', testMode: true },
  );
  const [headersText, setHeadersText] = useState(formatHeaders(initial?.headers));
  const [assertionsText, setAssertionsText] = useState(
    seed?.testCase.assertions?.length ? JSON.stringify(seed.testCase.assertions, null, 2) : '',
  );
  const [operationName, setOperationName] = useState('');
  const [caseName, setCaseName] = useState(seed?.testCase.name ?? '');
  const [collectionId, setCollectionId] = useState<string>(seed?.collectionId ?? '');
  const [newCollection, setNewCollection] = useState('');
  const [runId, setRunId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  const { data: services } = useServices();
  const { data: collections } = useTestCollections();
  const send = useSendTestRun();
  const startSuite = useStartTestSuite();
  const saveCollection = useSaveTestCollection();
  const { data: run } = useTestRun(runId);

  const candidates = useMemo(
    () =>
      (services ?? [])
        .filter((s) => !environment || !s.environment || s.environment === environment)
        .sort((a, b) => a.name.localeCompare(b.name)),
    [services, environment],
  );
  const service = candidates.find((s) => s.name === request.serviceName) ?? null;
  const { data: operations } = useServiceOperations(service?.id ?? null);
  const protocolOperations = (operations ?? []).filter((o) =>
    request.protocol === 'GRAPHQL' ? o.protocol === 'GRAPHQL' || o.protocol === 'HTTP' : o.protocol === request.protocol,
  );

  const update = (patch: Partial<TestRequest>) => setRequest((r) => ({ ...r, ...patch }));

  const parsedAssertions = useMemo((): { value: Assertion[]; error: string | null } => {
    if (!assertionsText.trim()) return { value: [], error: null };
    try {
      const value = JSON.parse(assertionsText);
      if (!Array.isArray(value)) return { value: [], error: 'Assertions must be a JSON array.' };
      if (value.some((a) => !a || typeof a !== 'object' || !a.target)) {
        return { value: [], error: 'Every assertion needs a target: {"entry": true} or {"service": …, "operation": …}.' };
      }
      return { value, error: null };
    } catch (e) {
      return { value: [], error: `Invalid JSON: ${(e as Error).message}` };
    }
  }, [assertionsText]);

  const built = (): TestRequest => cleanRequest({ ...request, environment, headers: parseHeaders(headersText) });

  const chooseOperation = (name: string) => {
    setOperationName(name);
    const operation = protocolOperations.find((o) => o.name === name);
    if (!operation) return;
    const template = request.protocol === 'GRAPHQL' ? GRAPHQL_TEMPLATE : templateFor(operation);
    const fields = requestFor(operation);
    update({ ...fields, protocol: request.protocol === 'GRAPHQL' ? 'GRAPHQL' : fields.protocol, ...(template && !request.body ? { body: template } : {}) });
  };

  const applyTemplate = (operation: CatalogOperation | undefined) => {
    const template = request.protocol === 'GRAPHQL' ? GRAPHQL_TEMPLATE : operation && templateFor(operation);
    if (template) update({ body: template });
  };

  const addAssertion = (assertion: Assertion) => {
    const current = parsedAssertions.error ? [] : parsedAssertions.value;
    setAssertionsText(JSON.stringify([...current, assertion], null, 2));
  };

  const onSend = async () => {
    setError(null);
    setSaved(null);
    try {
      if (parsedAssertions.value.length) {
        const suite = await startSuite.mutateAsync({
          name: caseName || 'Test Studio',
          environment,
          cases: [{ name: caseName || 'Test Studio', request: built(), assertions: parsedAssertions.value }],
        });
        setRunId(suite.runs?.[0]?.id ?? null);
      } else {
        setRunId((await send.mutateAsync(built())).id);
      }
    } catch (err) {
      setError(errorMessage(err, 'Could not send the request.'));
    }
  };

  const onSave = async () => {
    setError(null);
    setSaved(null);
    const testCase: TestCase = {
      name: caseName.trim(),
      // Saved without the environment: a collection runs in whichever one its suite picks.
      request: { ...built(), environment: null, serviceId: null },
      assertions: parsedAssertions.value.length ? parsedAssertions.value : null,
    };
    try {
      const existing = collections?.find((c) => c.id === collectionId);
      if (existing) {
        const cases = existing.cases.some((c) => c.name === testCase.name)
          ? existing.cases.map((c) => (c.name === testCase.name ? testCase : c))
          : [...existing.cases, testCase];
        await saveCollection.mutateAsync({ id: existing.id, name: existing.name, description: existing.description, cases });
        setSaved(`Saved to ${existing.name}.`);
      } else {
        const created = await saveCollection.mutateAsync({ name: newCollection.trim(), description: null, cases: [testCase] });
        setCollectionId(created.id);
        setNewCollection('');
        setSaved(`Saved to ${created.name}.`);
      }
    } catch (err) {
      setError(errorMessage(err, 'Could not save the case.'));
    }
  };

  const needsService = request.protocol !== 'MESSAGING';
  const canSend =
    !parsedAssertions.error &&
    (request.protocol === 'MESSAGING'
      ? !!request.topic
      : !!request.serviceName && (request.protocol === 'GRPC' ? !!request.grpcMethod : !!request.path));
  const canSave = canSend && !!caseName.trim() && (!!collectionId || !!newCollection.trim());
  const selectedOperation = protocolOperations.find((o) => o.name === operationName);

  return (
    <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-2">
      <div className="space-y-4 overflow-y-auto rounded-lg border border-charcoal-700 bg-charcoal-900 p-4">
        <div className="flex gap-1" role="tablist">
          {PROTOCOLS.map((p) => (
            <button
              key={p.value}
              type="button"
              role="tab"
              aria-selected={request.protocol === p.value}
              onClick={() => {
                setOperationName('');
                update({ protocol: p.value, ...(p.value === 'GRAPHQL' ? { method: 'POST', path: '/graphql' } : {}) });
              }}
              className={`rounded-md px-3 py-1.5 text-sm ${request.protocol === p.value ? 'bg-emerald-500/15 text-emerald-300' : 'text-gray-400 hover:bg-charcoal-800'}`}
            >
              {p.label}
            </button>
          ))}
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <label className="text-xs text-gray-400">
            {needsService ? 'Service' : 'Browse topics of (optional)'}
            <select
              aria-label="Service"
              value={request.serviceName ?? ''}
              onChange={(e) => {
                setOperationName('');
                update({ serviceName: e.target.value || null });
              }}
              className={`${field} mt-1`}
            >
              <option value="">Choose a service…</option>
              {candidates.map((s) => (
                <option key={s.id} value={s.name}>
                  {s.name}
                </option>
              ))}
            </select>
          </label>
          <label className="text-xs text-gray-400">
            Operation
            <select
              aria-label="Operation"
              value={operationName}
              onChange={(e) => chooseOperation(e.target.value)}
              disabled={!service || protocolOperations.length === 0}
              className={`${field} mt-1 disabled:opacity-50`}
            >
              <option value="">{!service ? '—' : protocolOperations.length ? 'From the catalog…' : 'None known yet'}</option>
              {protocolOperations.map((o) => (
                <option key={`${o.protocol}|${o.name}`} value={o.name}>
                  {o.name}
                  {o.callsLast24h > 0 ? ` · ${o.callsLast24h}/24h` : ''}
                </option>
              ))}
            </select>
          </label>
        </div>

        {(request.protocol === 'HTTP' || request.protocol === 'GRAPHQL') && (
          <div className="flex gap-2">
            <select aria-label="Method" value={request.method ?? 'GET'} onChange={(e) => update({ method: e.target.value })} className={`${field} w-28`}>
              {METHODS.map((m) => (
                <option key={m}>{m}</option>
              ))}
            </select>
            <Input aria-label="Path" placeholder="/orders" value={request.path ?? ''} onChange={(e) => update({ path: e.target.value })} className="font-mono" />
          </div>
        )}
        {request.protocol === 'GRPC' && (
          <Input
            aria-label="gRPC method"
            placeholder="package.Service/Method"
            value={request.grpcMethod ?? ''}
            onChange={(e) => update({ grpcMethod: e.target.value })}
            className="font-mono"
          />
        )}
        {request.protocol === 'MESSAGING' && (
          <div className="grid gap-2 sm:grid-cols-2">
            <Input aria-label="Topic" placeholder="topic" value={request.topic ?? ''} onChange={(e) => update({ topic: e.target.value })} className="font-mono" />
            <Input aria-label="Key" placeholder="key (optional)" value={request.key ?? ''} onChange={(e) => update({ key: e.target.value })} className="font-mono" />
          </div>
        )}

        <label className="block text-xs text-gray-400">
          Headers <span className="text-gray-600">— one per line, Name: value</span>
          <textarea aria-label="Headers" rows={2} value={headersText} onChange={(e) => setHeadersText(e.target.value)} className={`${mono} mt-1`} />
        </label>

        <div className="text-xs text-gray-400">
          <div className="flex items-center justify-between">
            <label htmlFor="studio-body">{request.protocol === 'MESSAGING' ? 'Message' : 'Body'}</label>
            {(selectedOperation?.requestSchema || request.protocol === 'GRAPHQL') && (
              <button type="button" onClick={() => applyTemplate(selectedOperation)} className="flex items-center gap-1 text-emerald-400 hover:text-emerald-300">
                <Wand2 className="h-3 w-3" /> Template from schema
              </button>
            )}
          </div>
          <textarea id="studio-body" aria-label="Body" rows={8} value={request.body ?? ''} onChange={(e) => update({ body: e.target.value })} className={`${mono} mt-1`} />
        </div>

        <label className="flex items-center gap-2 text-sm text-gray-300">
          <input type="checkbox" checked={request.testMode !== false} onChange={(e) => update({ testMode: e.target.checked })} />
          Test mode <span className="text-xs text-gray-500">— services see baggage sdna.test=1 and can skip real side effects</span>
        </label>

        <div className="text-xs text-gray-400">
          <div className="flex items-center justify-between">
            <label htmlFor="studio-assertions">
              Assertions <span className="text-gray-600">— JSON; checked after the run</span>
            </label>
            <span className="flex gap-3">
              <button type="button" onClick={() => addAssertion({ target: { entry: true }, status: 200 })} className="text-emerald-400 hover:text-emerald-300">
                + entry status
              </button>
              {run?.hops && (
                <button type="button" onClick={() => setAssertionsText(JSON.stringify(assertionsFromRun(run), null, 2))} className="text-emerald-400 hover:text-emerald-300">
                  From this run
                </button>
              )}
            </span>
          </div>
          <textarea
            id="studio-assertions"
            aria-label="Assertions"
            rows={6}
            placeholder={'[\n  {"target": {"entry": true}, "status": 201},\n  {"target": {"service": "payment-service", "operation": "Charge"}, "latencyMs": {"lt": 500}}\n]'}
            value={assertionsText}
            onChange={(e) => setAssertionsText(e.target.value)}
            className={`${mono} mt-1`}
          />
          {parsedAssertions.error && <span className="text-rose-400">{parsedAssertions.error}</span>}
        </div>

        {error && <p className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-sm text-rose-400">{error}</p>}

        <div className="flex flex-wrap items-end gap-2 border-t border-charcoal-700 pt-4">
          <Button onClick={onSend} disabled={!canSend} isLoading={send.isPending || startSuite.isPending}>
            <Send className="mr-2 h-4 w-4" /> Send{parsedAssertions.value.length ? ' and check' : ''}
          </Button>
          <div className="ml-auto flex flex-wrap items-center gap-2">
            <Input aria-label="Case name" placeholder="Case name" value={caseName} onChange={(e) => setCaseName(e.target.value)} className="w-40" />
            <select aria-label="Collection" value={collectionId} onChange={(e) => setCollectionId(e.target.value)} className={`${field} w-40`}>
              <option value="">New collection…</option>
              {(collections ?? []).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
            {!collectionId && (
              <Input aria-label="New collection name" placeholder="Collection name" value={newCollection} onChange={(e) => setNewCollection(e.target.value)} className="w-40" />
            )}
            <Button variant="outline" onClick={onSave} disabled={!canSave} isLoading={saveCollection.isPending}>
              <Save className="mr-2 h-4 w-4" /> Save
            </Button>
          </div>
          {saved && <p className="w-full text-right text-xs text-emerald-400">{saved}</p>}
        </div>
      </div>

      <div className="overflow-y-auto rounded-lg border border-charcoal-700 bg-charcoal-900 p-4">
        {run ? (
          <RunView run={run} onAssertHop={(hop: Hop) => addAssertion(hopAssertion(hop))} />
        ) : (
          <div className="flex h-full min-h-48 items-center justify-center text-center text-sm text-gray-500">
            Send a request to see it travel through your services —
            <br />
            every hop, what it received and what it returned.
          </div>
        )}
      </div>
    </div>
  );
}
