import { useState } from 'react';
import axios from 'axios';
import { Pencil, Play, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import type { TestCase, TestCollection } from '@/api/testing.api';
import { useDeleteTestCollection, useSaveTestCollection, useStartTestSuite, useTestCollections } from '@/hooks/useTesting';

function describe(c: TestCase) {
  const r = c.request;
  if (r.protocol === 'MESSAGING') return `Kafka → ${r.topic}`;
  if (r.protocol === 'GRPC') return `${r.serviceName} ${r.grpcMethod}`;
  return `${r.serviceName} ${r.method} ${r.path}`;
}

/** Saved collections: run one as a suite, open a case in the composer, or remove cases. */
export function CollectionsPanel({
  environment,
  onOpenCase,
  onSuiteStarted,
}: {
  environment: string | null;
  onOpenCase: (testCase: TestCase, collectionId: string) => void;
  onSuiteStarted: (suiteId: string) => void;
}) {
  const { data: collections, isLoading } = useTestCollections();
  const startSuite = useStartTestSuite();
  const saveCollection = useSaveTestCollection();
  const deleteCollection = useDeleteTestCollection();
  const [error, setError] = useState<string | null>(null);

  const run = async (collection: TestCollection) => {
    setError(null);
    try {
      onSuiteStarted((await startSuite.mutateAsync({ collectionId: collection.id, environment })).id);
    } catch (err) {
      setError((axios.isAxiosError(err) && err.response?.data?.message) || 'Could not start the suite.');
    }
  };

  const removeCase = (collection: TestCollection, name: string) =>
    saveCollection.mutate({ id: collection.id, name: collection.name, description: collection.description, cases: collection.cases.filter((c) => c.name !== name) });

  if (isLoading) return <p className="text-sm text-gray-400">Loading collections…</p>;
  if (!collections?.length) {
    return (
      <p className="rounded-lg border border-charcoal-700 bg-charcoal-900 p-6 text-sm text-gray-400">
        No collections yet. Compose a request, name the case and save it — or keep flow files in your repo and run them with{' '}
        <code className="font-mono text-gray-200">sdna test run flows/</code>.
      </p>
    );
  }

  return (
    <div className="space-y-4 overflow-y-auto">
      {error && <p className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-sm text-rose-400">{error}</p>}
      {collections.map((collection) => (
        <div key={collection.id} className="rounded-lg border border-charcoal-700 bg-charcoal-900" data-testid="collection">
          <div className="flex items-center gap-3 border-b border-charcoal-700 p-3">
            <div className="min-w-0 flex-1">
              <div className="font-medium text-white">{collection.name}</div>
              <div className="text-xs text-gray-500">
                {collection.cases.length} {collection.cases.length === 1 ? 'case' : 'cases'}
                {collection.description ? ` · ${collection.description}` : ''}
              </div>
            </div>
            <Button size="sm" onClick={() => run(collection)} disabled={!collection.cases.length} isLoading={startSuite.isPending && startSuite.variables?.collectionId === collection.id}>
              <Play className="mr-1 h-3.5 w-3.5" /> Run{environment ? ` in ${environment}` : ''}
            </Button>
            <Button
              size="sm"
              variant="ghost"
              aria-label={`Delete ${collection.name}`}
              onClick={() => window.confirm(`Delete collection "${collection.name}"?`) && deleteCollection.mutate(collection.id)}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
          <ul className="divide-y divide-charcoal-800">
            {collection.cases.map((c) => (
              <li key={c.name} className="flex items-center gap-3 px-3 py-2 text-sm">
                <span className="text-gray-200">{c.name}</span>
                <span className="truncate font-mono text-xs text-gray-500">{describe(c)}</span>
                <span className="ml-auto shrink-0 text-xs text-gray-500">{c.assertions?.length ?? 0} checks</span>
                <button type="button" aria-label={`Open ${c.name}`} onClick={() => onOpenCase(c, collection.id)} className="text-gray-500 hover:text-white">
                  <Pencil className="h-3.5 w-3.5" />
                </button>
                <button type="button" aria-label={`Remove ${c.name}`} onClick={() => removeCase(collection, c.name)} className="text-gray-500 hover:text-rose-400">
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}
