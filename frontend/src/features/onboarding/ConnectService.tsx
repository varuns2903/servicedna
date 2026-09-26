import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { Check, CheckCircle2, Copy, Loader2 } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useOrganizationStore } from '@/stores/useOrganizationStore';
import { useServices } from '@/hooks/useServices';
import { useCreateIngestionKey } from '@/hooks/useIngestionKeys';
import { snippetFor, SNIPPET_TARGETS, type SnippetTarget } from './snippets';

/** ServiceDNA's base URL (the API URL without /api/v1), as services will reach it. */
const serviceDnaUrl = (import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1').replace(/\/api\/v1\/?$/, '');

/**
 * Onboarding: create a key, copy two lines for your stack, and watch the service appear. Modelled
 * on Sentry/Datadog onboarding — the page waits live for the first signal.
 */
export function ConnectService() {
  const orgId = useOrganizationStore((s) => s.selectedOrganizationId);
  const createKey = useCreateIngestionKey();
  const [key, setKey] = useState<string | null>(null);
  const [keyName, setKeyName] = useState('my first service');
  const [keyError, setKeyError] = useState<string | null>(null);
  const [target, setTarget] = useState<SnippetTarget>('node');
  const [environment, setEnvironment] = useState('dev');

  const openedAt = useRef(new Date());
  const { data: services } = useServices({ refetchInterval: key ? 3000 : false });
  const connected = useMemo(
    () =>
      (services || []).find(
        (s) => s.lastTelemetryAt && new Date(s.lastTelemetryAt).getTime() >= openedAt.current.getTime(),
      ),
    [services],
  );

  const handleCreateKey = (e: React.FormEvent) => {
    e.preventDefault();
    if (!orgId) return;
    setKeyError(null);
    createKey.mutate(
      { orgId, name: keyName },
      {
        onSuccess: (created) => setKey(created.key),
        onError: (err) =>
          setKeyError((axios.isAxiosError(err) && err.response?.data?.message) || 'Could not create a key.'),
      },
    );
  };

  const snippet = snippetFor(target, { url: serviceDnaUrl, key: key ?? 'sdna_ik_…', environment });

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-4 sm:p-8">
      <div>
        <h1 className="text-2xl font-semibold text-white">Connect a service</h1>
        <p className="mt-1 text-sm text-gray-400">
          Add one dependency and two environment variables. The service registers itself, sends traces, and reports its
          health — no manual registration.
        </p>
      </div>

      <Step number={1} title="Create an ingestion key" done={!!key}>
        {key ? (
          <p className="text-sm text-gray-400">
            Key created — it's already in the snippet below. You can manage keys in Settings → Integrations.
          </p>
        ) : (
          <form onSubmit={handleCreateKey} className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <div className="flex-1">
              <label className="mb-1 block text-sm text-gray-400">Key name</label>
              <Input value={keyName} onChange={(e) => setKeyName(e.target.value)} required />
            </div>
            <Button type="submit" disabled={!keyName.trim() || createKey.isPending}>
              {createKey.isPending ? 'Creating...' : 'Create key'}
            </Button>
          </form>
        )}
        {keyError && <p className="mt-2 text-sm text-rose-400">{keyError}</p>}
      </Step>

      <Step number={2} title="Add ServiceDNA to your service" done={!!connected}>
        <div className="flex flex-wrap gap-2">
          {SNIPPET_TARGETS.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setTarget(t.id)}
              className={`rounded-md border px-3 py-1.5 text-sm ${
                target === t.id
                  ? 'border-emerald-500 bg-emerald-500/10 text-white'
                  : 'border-charcoal-700 text-gray-400 hover:text-gray-200'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
        <div className="mt-3 flex items-center gap-2 text-sm text-gray-400">
          <label htmlFor="environment">Environment</label>
          <Input
            id="environment"
            className="h-8 w-32"
            value={environment}
            onChange={(e) => setEnvironment(e.target.value.replace(/\s/g, ''))}
          />
        </div>
        {snippet.map((block) => (
          <CodeBlock key={block.caption} caption={block.caption} code={block.code} />
        ))}
      </Step>

      <Step number={3} title="Start the service" done={!!connected}>
        {connected ? (
          <div className="flex items-center justify-between gap-3 rounded-md border border-emerald-500/30 bg-emerald-500/10 p-3">
            <div className="flex items-center gap-2 text-sm text-emerald-300">
              <CheckCircle2 className="h-5 w-5" />
              <span>
                <strong>{connected.name}</strong>
                {connected.environment && ` (${connected.environment})`} is connected.
              </span>
            </div>
            <Link to={`/services/${connected.id}`}>
              <Button size="sm">View service</Button>
            </Link>
          </div>
        ) : key ? (
          <div className="flex items-center gap-2 text-sm text-gray-400">
            <Loader2 className="h-4 w-4 animate-spin" />
            Waiting for the first signal from your service…
          </div>
        ) : (
          <p className="text-sm text-gray-500">Create a key first.</p>
        )}
      </Step>
    </div>
  );
}

function Step({
  number,
  title,
  done,
  children,
}: {
  number: number;
  title: string;
  done: boolean;
  children: React.ReactNode;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-3">
          <span
            className={`flex h-6 w-6 items-center justify-center rounded-full text-xs ${
              done ? 'bg-emerald-500 text-charcoal-900' : 'bg-charcoal-700 text-gray-300'
            }`}
          >
            {done ? <Check className="h-3.5 w-3.5" /> : number}
          </span>
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">{children}</CardContent>
    </Card>
  );
}

function CodeBlock({ caption, code }: { caption: string; code: string }) {
  const [copied, setCopied] = useState(false);
  useEffect(() => setCopied(false), [code]);
  return (
    <div className="mt-3">
      <p className="mb-1 text-xs text-gray-500">{caption}</p>
      <div className="relative">
        <pre className="overflow-x-auto rounded-md border border-charcoal-700 bg-charcoal-900 p-3 pr-10 text-xs text-gray-200">
          {code}
        </pre>
        <button
          type="button"
          onClick={async () => {
            await navigator.clipboard.writeText(code);
            setCopied(true);
          }}
          className="absolute right-2 top-2 rounded p-1 text-gray-400 hover:text-white"
          title="Copy"
        >
          {copied ? <Check className="h-4 w-4 text-emerald-400" /> : <Copy className="h-4 w-4" />}
        </button>
      </div>
    </div>
  );
}
