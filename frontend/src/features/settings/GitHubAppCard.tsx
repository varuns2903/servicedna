import { useState } from 'react';
import axios from 'axios';
import { formatDistanceToNow } from 'date-fns';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, RefreshCw, XCircle } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { GitHubAppApi, type GitHubSyncResult } from '@/api/githubApp.api';

/**
 * The ServiceDNA GitHub App: install it on a GitHub organization and every repository's
 * servicedna.yaml stays in step, and pull requests get a ServiceDNA check (servicedna.yaml
 * validated, flows/ run) — no CI setup per repository.
 */
export function GitHubAppCard({ orgId }: { orgId: string }) {
  const queryClient = useQueryClient();
  const key = ['organizations', orgId, 'github', 'app'];
  const { data: status } = useQuery({ queryKey: key, queryFn: () => GitHubAppApi.status(orgId) });
  const [results, setResults] = useState<Record<number, GitHubSyncResult[]>>({});
  const [error, setError] = useState<string | null>(null);
  const sync = useMutation({
    mutationFn: (installationId: number) => GitHubAppApi.sync(orgId, installationId),
    onSuccess: (data, installationId) => {
      setResults((r) => ({ ...r, [installationId]: data }));
      queryClient.invalidateQueries({ queryKey: key });
    },
    onError: (err) => setError((axios.isAxiosError(err) && err.response?.data?.message) || 'Sync failed.'),
  });
  const disconnect = useMutation({
    mutationFn: (installationId: number) => GitHubAppApi.disconnect(orgId, installationId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key }),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>GitHub App</CardTitle>
        <p className="text-sm text-gray-400">
          Keeps services in step with each repository's <code className="font-mono">servicedna.yaml</code> on every push, and checks
          pull requests: the file validated, and <code className="font-mono">flows/</code> run through ServiceDNA. No CI setup per repository.
        </p>
      </CardHeader>
      <CardContent className="space-y-4">
        {!status ? (
          <p className="text-sm text-gray-500">Loading…</p>
        ) : !status.configured ? (
          <p className="text-sm text-gray-400">
            Not set up on this ServiceDNA. An operator creates the app once with{' '}
            <code className="font-mono text-gray-200">sdna github create-app --url &lt;ServiceDNA URL&gt;</code> and gives the backend the
            settings it writes. Until then, the <span className="text-gray-200">GitHub Action</span> does the same per repository.
          </p>
        ) : (
          <>
            {status.installUrl && (
              <a href={status.installUrl} className="inline-flex">
                <Button size="sm">Install on GitHub</Button>
              </a>
            )}
            {status.installations.length === 0 ? (
              <p className="text-sm text-gray-500">Not installed on a GitHub organization yet.</p>
            ) : (
              <ul className="space-y-3" data-testid="github-installations">
                {status.installations.map((i) => (
                  <li key={i.installationId} className="rounded-md border border-charcoal-700 bg-charcoal-900 p-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-sm text-gray-100">{i.account}</span>
                      <span className="text-xs text-gray-500">
                        {i.lastSyncedAt ? `synced ${formatDistanceToNow(new Date(i.lastSyncedAt), { addSuffix: true })}` : 'syncing…'}
                      </span>
                      <div className="ml-auto flex gap-2">
                        {status.installUrl && (
                          <Button size="sm" variant="outline" onClick={() => sync.mutate(i.installationId)} isLoading={sync.isPending && sync.variables === i.installationId}>
                            <RefreshCw className="mr-1 h-3.5 w-3.5" /> Sync now
                          </Button>
                        )}
                        {status.installUrl && (
                          <Button
                            size="sm"
                            variant="outline"
                            className="border-rose-500/20 text-rose-400 hover:bg-rose-500/10"
                            onClick={() => window.confirm(`Disconnect ${i.account}? Uninstall the app on GitHub to stop it completely.`) && disconnect.mutate(i.installationId)}
                          >
                            Disconnect
                          </Button>
                        )}
                      </div>
                    </div>
                    {results[i.installationId] && (
                      <ul className="mt-2 space-y-0.5 text-xs">
                        {results[i.installationId].length === 0 && <li className="text-gray-500">No repositories with a servicedna.yaml.</li>}
                        {results[i.installationId].map((r) => (
                          <li key={r.repository} className="flex items-start gap-1.5">
                            {r.ok ? <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-emerald-400" /> : <XCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-rose-400" />}
                            <span className="text-gray-300">
                              {r.repository} {r.service && <span className="text-gray-500">→ {r.service}</span>} — {r.message}
                            </span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
        {error && <p className="text-sm text-rose-400">{error}</p>}
      </CardContent>
    </Card>
  );
}
