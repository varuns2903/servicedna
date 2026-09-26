import { useState } from 'react';
import axios from 'axios';
import { useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, XCircle } from 'lucide-react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { apiClient } from '@/api/client';
import { useOrganizationStore } from '@/stores/useOrganizationStore';

interface Repository {
  fullName: string;
  name: string;
  url: string;
  description: string | null;
  archived: boolean;
  hasManifest: boolean;
  manifestError: string | null;
  service: string;
  owner: string | null;
  tier: string | null;
  registered: boolean;
}

interface Imported {
  repository: string;
  service: string;
  ok: boolean;
  message: string;
}

function errorMessage(err: unknown, fallback: string) {
  return (axios.isAxiosError(err) && err.response?.data?.message) || fallback;
}

/**
 * First-time setup: list a GitHub organization's repositories, see what each one's
 * servicedna.yaml says, and register the chosen ones. The token is only used for this request.
 */
export function GitHubImportModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const orgId = useOrganizationStore((s) => s.selectedOrganizationId);
  const queryClient = useQueryClient();
  const [owner, setOwner] = useState('');
  const [token, setToken] = useState('');
  const [repos, setRepos] = useState<Repository[] | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [results, setResults] = useState<Imported[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const preview = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setResults(null);
    try {
      const { data } = await apiClient.post<Repository[]>(`/organizations/${orgId}/github/import/preview`, { owner: owner.trim(), token: token.trim() || null });
      setRepos(data);
      setSelected(new Set(data.filter((r) => !r.archived && !r.registered && !r.manifestError).map((r) => r.fullName)));
    } catch (err) {
      setError(errorMessage(err, 'Could not list the repositories.'));
    } finally {
      setBusy(false);
    }
  };

  const runImport = async () => {
    setBusy(true);
    setError(null);
    try {
      const { data } = await apiClient.post<Imported[]>(`/organizations/${orgId}/github/import`, {
        owner: owner.trim(),
        token: token.trim() || null,
        repositories: [...selected],
      });
      setResults(data);
      queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'services'] });
    } catch (err) {
      setError(errorMessage(err, 'Import failed.'));
    } finally {
      setBusy(false);
    }
  };

  const toggle = (name: string) => {
    const next = new Set(selected);
    if (next.has(name)) next.delete(name);
    else next.add(name);
    setSelected(next);
  };

  return (
    <Modal open={open} onClose={onClose} title="Import from GitHub" className="max-w-3xl">
      <form onSubmit={preview} className="space-y-3">
        <p className="text-sm text-gray-400">
          Lists the repositories and reads each one's <code className="font-mono text-gray-300">servicedna.yaml</code> (owner, tier, SLO, alerts). For
          private repositories, use a fine-grained token with read-only <em>Contents</em> and <em>Metadata</em> access; it isn't stored.
        </p>
        <div className="flex flex-col gap-2 sm:flex-row">
          <Input aria-label="GitHub organization" placeholder="GitHub organization or user" value={owner} onChange={(e) => setOwner(e.target.value)} required />
          <Input aria-label="GitHub token" type="password" placeholder="Token (optional for public repos)" value={token} onChange={(e) => setToken(e.target.value)} />
          <Button type="submit" variant="outline" isLoading={busy && !repos} disabled={!owner.trim()}>
            List repositories
          </Button>
        </div>
      </form>

      {error && <p className="mt-3 rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-sm text-rose-400">{error}</p>}

      {repos && !results && (
        <div className="mt-4 space-y-3">
          {repos.length === 0 ? (
            <p className="text-sm text-gray-500">No repositories found.</p>
          ) : (
            <ul className="max-h-80 divide-y divide-charcoal-800 overflow-y-auto rounded-md border border-charcoal-700" data-testid="github-repos">
              {repos.map((r) => {
                const disabled = r.archived || !!r.manifestError;
                return (
                  <li key={r.fullName} className={`flex items-start gap-3 px-3 py-2 text-sm ${disabled ? 'opacity-60' : ''}`}>
                    <input type="checkbox" aria-label={`Import ${r.fullName}`} className="mt-1" checked={selected.has(r.fullName)} disabled={disabled} onChange={() => toggle(r.fullName)} />
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <a href={r.url} target="_blank" rel="noreferrer" className="text-gray-100 hover:underline">
                          {r.fullName}
                        </a>
                        <span className="text-gray-500">→</span>
                        <span className="font-mono text-xs text-gray-300">{r.service}</span>
                        {r.tier && <Badge variant={r.tier === 'critical' ? 'danger' : 'default'}>{r.tier}</Badge>}
                        {r.owner && <span className="text-xs text-gray-400">{r.owner}</span>}
                      </div>
                      <div className="text-xs text-gray-500">
                        {r.archived
                          ? 'archived'
                          : r.manifestError
                            ? <span className="text-rose-400">{r.manifestError}</span>
                            : r.hasManifest
                              ? 'servicedna.yaml'
                              : 'no servicedna.yaml — name, description and link only'}
                        {r.registered && ' · already registered (importing updates it)'}
                      </div>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
          <div className="flex justify-end">
            <Button onClick={runImport} disabled={selected.size === 0} isLoading={busy}>
              Import {selected.size} {selected.size === 1 ? 'repository' : 'repositories'}
            </Button>
          </div>
        </div>
      )}

      {results && (
        <ul className="mt-4 space-y-1 text-sm" data-testid="github-results">
          {results.map((r) => (
            <li key={r.repository} className="flex items-start gap-2">
              {r.ok ? <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" /> : <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-400" />}
              <span>
                <span className="text-gray-100">{r.service}</span> <span className="text-gray-500">({r.repository})</span> — <span className="text-gray-400">{r.message}</span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  );
}
