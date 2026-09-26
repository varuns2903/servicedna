import { useState } from 'react';
import axios from 'axios';
import { formatDistanceToNow } from 'date-fns';
import { Check, Copy, KeyRound } from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { apiClient } from '@/api/client';

interface ApiToken {
  id: string;
  name: string;
  prefix: string;
  createdAt: string;
  expiresAt: string | null;
  lastUsedAt: string | null;
  token: string | null;
}

const EXPIRY = [
  { days: 30, label: '30 days' },
  { days: 90, label: '90 days' },
  { days: 365, label: '1 year' },
  { days: 0, label: 'Never' },
];

/** Personal API tokens: the CLI (`sdna login --token`) and CI (`SDNA_TOKEN`), acting as you. */
export function ApiTokensCard() {
  const queryClient = useQueryClient();
  const { data: tokens } = useQuery({
    queryKey: ['users', 'me', 'tokens'],
    queryFn: async () => (await apiClient.get<ApiToken[]>('/users/me/tokens')).data,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['users', 'me', 'tokens'] });
  const create = useMutation({
    mutationFn: async (body: { name: string; expiresInDays: number | null }) => (await apiClient.post<ApiToken>('/users/me/tokens', body)).data,
    onSuccess: refresh,
  });
  const revoke = useMutation({ mutationFn: (id: string) => apiClient.delete(`/users/me/tokens/${id}`), onSuccess: refresh });
  const [name, setName] = useState('');
  const [days, setDays] = useState(90);
  const [newToken, setNewToken] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    create.mutate(
      { name: name.trim(), expiresInDays: days || null },
      {
        onSuccess: (t) => {
          setName('');
          setNewToken(t.token);
          setCopied(false);
        },
        onError: (err) => setError((axios.isAxiosError(err) && err.response?.data?.message) || 'Could not create the token.'),
      },
    );
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>API Tokens</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-gray-400">
          For the CLI and CI: <code className="font-mono text-gray-300">sdna login --token …</code>, or set{' '}
          <code className="font-mono text-gray-300">SDNA_URL</code>, <code className="font-mono text-gray-300">SDNA_TOKEN</code> and{' '}
          <code className="font-mono text-gray-300">SDNA_ORG</code> in a pipeline. A token acts as you, with your roles.
        </p>
        <form onSubmit={submit} className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <label className="mb-1 block text-sm text-gray-400">Token name</label>
            <Input aria-label="Token name" value={name} onChange={(e) => setName(e.target.value)} placeholder="GitHub Actions" required />
          </div>
          <div>
            <label className="mb-1 block text-sm text-gray-400">Expires</label>
            <select
              aria-label="Expires"
              value={days}
              onChange={(e) => setDays(Number(e.target.value))}
              className="h-10 rounded-md border border-charcoal-600 bg-charcoal-800 px-2 text-sm text-gray-100"
            >
              {EXPIRY.map((x) => (
                <option key={x.days} value={x.days}>
                  {x.label}
                </option>
              ))}
            </select>
          </div>
          <Button type="submit" disabled={!name.trim() || create.isPending}>
            {create.isPending ? 'Creating...' : 'Create Token'}
          </Button>
        </form>
        {newToken && (
          <div className="space-y-2 rounded-md border border-emerald-500/30 bg-emerald-500/10 p-3" data-testid="new-token">
            <p className="text-xs text-emerald-300">Copy this token now — it won't be shown again.</p>
            <div className="flex items-center gap-2">
              <code className="flex-1 truncate rounded bg-charcoal-900 px-2 py-1 text-xs text-gray-200">{newToken}</code>
              <button
                type="button"
                onClick={async () => {
                  await navigator.clipboard.writeText(newToken);
                  setCopied(true);
                }}
                className="rounded p-1 text-gray-300 hover:text-white"
                title="Copy token"
              >
                {copied ? <Check className="h-4 w-4 text-emerald-400" /> : <Copy className="h-4 w-4" />}
              </button>
            </div>
          </div>
        )}
        {error && <div className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">{error}</div>}
        {tokens && tokens.length > 0 && (
          <ul className="space-y-2">
            {tokens.map((t) => {
              const expired = t.expiresAt && new Date(t.expiresAt) < new Date();
              return (
                <li key={t.id} className="flex items-center justify-between gap-2 rounded-md border border-charcoal-700 bg-charcoal-900 px-3 py-2">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <KeyRound className="h-4 w-4 shrink-0 text-gray-500" />
                      <span className="truncate text-sm text-gray-200">{t.name}</span>
                      <code className="text-xs text-gray-500">{t.prefix}…</code>
                      {expired && <Badge variant="danger">Expired</Badge>}
                    </div>
                    <p className="mt-0.5 text-xs text-gray-500">
                      {t.lastUsedAt ? `Last used ${formatDistanceToNow(new Date(t.lastUsedAt), { addSuffix: true })}` : 'Never used'}
                      {' · '}
                      {t.expiresAt ? `${expired ? 'expired' : 'expires'} ${formatDistanceToNow(new Date(t.expiresAt), { addSuffix: true })}` : 'never expires'}
                    </p>
                  </div>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => revoke.mutate(t.id)}
                    disabled={revoke.isPending}
                    className="shrink-0 border-rose-500/20 text-rose-400 hover:bg-rose-500/10"
                  >
                    Revoke
                  </Button>
                </li>
              );
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
