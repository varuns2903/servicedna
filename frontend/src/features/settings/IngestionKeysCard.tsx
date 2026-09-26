import { useState } from 'react';
import axios from 'axios';
import { formatDistanceToNow } from 'date-fns';
import { Check, Copy, KeyRound } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { useIngestionKeys, useCreateIngestionKey, useRevokeIngestionKey } from '@/hooks/useIngestionKeys';

interface IngestionKeysCardProps {
  orgId: string;
}

/** Organization-wide keys that SDKs, collectors and agents send telemetry with. */
export function IngestionKeysCard({ orgId }: IngestionKeysCardProps) {
  const { data: keys } = useIngestionKeys(orgId);
  const createKey = useCreateIngestionKey();
  const revokeKey = useRevokeIngestionKey();

  const [name, setName] = useState('');
  const [newKey, setNewKey] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    createKey.mutate(
      { orgId, name },
      {
        onSuccess: (created) => {
          setName('');
          setNewKey(created.key);
          setCopied(false);
        },
        onError: (err) =>
          setError((axios.isAxiosError(err) && err.response?.data?.message) || 'Could not create key.'),
      },
    );
  };

  const copy = async () => {
    if (!newKey) return;
    await navigator.clipboard.writeText(newKey);
    setCopied(true);
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Ingestion Keys</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-gray-400">
          One key per environment or collector is enough: services identify themselves through their telemetry, so
          SDKs, OpenTelemetry collectors and the ServiceDNA agent all send with an organization key.
        </p>

        <form onSubmit={handleCreate} className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <label className="mb-1 block text-sm text-gray-400">Key name</label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="prod collector" required />
          </div>
          <Button type="submit" disabled={!name.trim() || createKey.isPending}>
            {createKey.isPending ? 'Creating...' : 'Create Key'}
          </Button>
        </form>

        {newKey && (
          <div className="space-y-2 rounded-md border border-emerald-500/30 bg-emerald-500/10 p-3">
            <p className="text-xs text-emerald-300">Copy this key now — it won't be shown again.</p>
            <div className="flex items-center gap-2">
              <code className="flex-1 truncate rounded bg-charcoal-900 px-2 py-1 text-xs text-gray-200">{newKey}</code>
              <button type="button" onClick={copy} className="rounded p-1 text-gray-300 hover:text-white" title="Copy key">
                {copied ? <Check className="h-4 w-4 text-emerald-400" /> : <Copy className="h-4 w-4" />}
              </button>
            </div>
          </div>
        )}

        {error && (
          <div className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">{error}</div>
        )}

        {keys && keys.length > 0 ? (
          <ul className="space-y-2">
            {keys.map((key) => (
              <li
                key={key.id}
                className="flex items-center justify-between gap-2 rounded-md border border-charcoal-700 bg-charcoal-900 px-3 py-2"
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <KeyRound className="h-4 w-4 shrink-0 text-gray-500" />
                    <span className="truncate text-sm text-gray-200">{key.name}</span>
                    <code className="text-xs text-gray-500">{key.keyPrefix}…</code>
                    {key.revokedAt && <Badge variant="danger">Revoked</Badge>}
                  </div>
                  <p className="mt-0.5 text-xs text-gray-500">
                    {key.lastUsedAt
                      ? `Last used ${formatDistanceToNow(new Date(key.lastUsedAt), { addSuffix: true })}`
                      : 'Never used'}
                    {key.createdByEmail && ` · created by ${key.createdByEmail}`}
                  </p>
                </div>
                {!key.revokedAt && (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => revokeKey.mutate({ orgId, keyId: key.id })}
                    disabled={revokeKey.isPending}
                    className="shrink-0 text-rose-400 border-rose-500/20 hover:bg-rose-500/10"
                  >
                    Revoke
                  </Button>
                )}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-gray-500">No ingestion keys yet.</p>
        )}
      </CardContent>
    </Card>
  );
}
