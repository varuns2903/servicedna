import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { TestCase } from '@/api/testing.api';
import { useServices } from '@/hooks/useServices';
import { useEnvironments } from '@/hooks/useTesting';
import { Composer, type ComposerSeed } from './Composer';
import { CollectionsPanel } from './CollectionsPanel';
import { SuitesPanel } from './SuitesPanel';

type Tab = 'compose' | 'collections' | 'suites';

const TABS: { value: Tab; label: string }[] = [
  { value: 'compose', label: 'Compose' },
  { value: 'collections', label: 'Collections' },
  { value: 'suites', label: 'Suites' },
];

/**
 * Test Studio: send a request into any environment through its runner, watch every hop, turn it
 * into a saved case with assertions, and run collections as suites.
 */
export function TestStudio() {
  const [tab, setTab] = useState<Tab>('compose');
  const [chosen, setEnvironment] = useState<string | null>(null);
  const [search] = useSearchParams();
  const [seed, setSeed] = useState<{ key: number; value?: ComposerSeed }>({ key: 0 });
  const [suiteId, setSuiteId] = useState<string | null>(null);
  const { data: settings } = useEnvironments();
  const { data: services } = useServices();

  // Environments the services report, with whether test runs are allowed in each.
  const environments = useMemo(() => {
    const names = new Set<string>((settings ?? []).map((s) => s.environment));
    (services ?? []).forEach((s) => s.environment && names.add(s.environment));
    return [...names].sort().map((name) => {
      const setting = settings?.find((s) => s.environment === name);
      return { name, blocked: !!setting && setting.productionLike && !setting.allowTestRuns };
    });
  }, [settings, services]);

  // Until one is picked: the first environment test runs are allowed in.
  const environment = chosen ?? (environments.find((e) => !e.blocked) ?? environments[0])?.name ?? null;

  const blocked = environments.find((e) => e.name === environment)?.blocked;

  const openCase = (testCase: TestCase, collectionId: string) => {
    setSeed((s) => ({ key: s.key + 1, value: { testCase, collectionId } }));
    setTab('compose');
  };

  return (
    <div className="flex h-full flex-col">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-white">Test Studio</h1>
          <p className="text-sm text-gray-400">Send a request through your services and see what every one of them did.</p>
        </div>
        <label className="flex items-center gap-2 text-sm text-gray-400">
          Environment
          <select
            aria-label="Environment"
            value={environment ?? ''}
            onChange={(e) => setEnvironment(e.target.value || null)}
            className="rounded-md border border-charcoal-700 bg-charcoal-900 p-2 text-sm text-white"
          >
            {environments.length === 0 && <option value="">Any</option>}
            {environments.map((e) => (
              <option key={e.name} value={e.name}>
                {e.name}
                {e.blocked ? ' (test runs off)' : ''}
              </option>
            ))}
          </select>
        </label>
      </div>

      {blocked && (
        <p className="mb-3 rounded-md border border-amber-400/20 bg-amber-400/10 p-2 text-sm text-amber-300">
          {environment} looks like production, so test runs are off there. An admin can allow them in Settings → Integrations.
        </p>
      )}

      <div className="mb-3 flex gap-1 border-b border-charcoal-700" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            role="tab"
            aria-selected={tab === t.value}
            onClick={() => setTab(t.value)}
            className={`-mb-px border-b-2 px-3 py-2 text-sm ${tab === t.value ? 'border-emerald-500 text-white' : 'border-transparent text-gray-400 hover:text-gray-200'}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="flex min-h-0 flex-1 flex-col">
        {/* Kept mounted so a composed request and its run survive switching tabs. */}
        <div className={tab === 'compose' ? 'contents' : 'hidden'}>
          <Composer key={seed.key} environment={environment} seed={seed.value} initialRunId={seed.key === 0 ? search.get('run') : null} />
        </div>
        {tab === 'collections' && (
          <CollectionsPanel
            environment={environment}
            onOpenCase={openCase}
            onSuiteStarted={(id) => {
              setSuiteId(id);
              setTab('suites');
            }}
          />
        )}
        {tab === 'suites' && <SuitesPanel selectedId={suiteId} onSelect={setSuiteId} />}
      </div>
    </div>
  );
}
