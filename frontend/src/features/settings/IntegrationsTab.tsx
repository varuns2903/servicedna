import { useState } from 'react';
import axios from 'axios';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { Trash2 } from 'lucide-react';
import { useWebhooks, useCreateWebhook, useDeleteWebhook } from '@/hooks/useWebhooks';
import type { WebhookType } from '@/api/webhooks.api';

interface IntegrationsTabProps {
  orgId: string;
}

export function IntegrationsTab({ orgId }: IntegrationsTabProps) {
  const { data: webhooks, isLoading } = useWebhooks(orgId);
  const createWebhook = useCreateWebhook();
  const deleteWebhook = useDeleteWebhook();

  const [url, setUrl] = useState('');
  const [webhookType, setWebhookType] = useState<WebhookType>('SLACK');
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    createWebhook.mutate(
      { orgId, request: { url, webhookType } },
      {
        onSuccess: () => setUrl(''),
        onError: (err) => {
          setError(
            (axios.isAxiosError(err) && err.response?.data?.message) || 'Could not add webhook.'
          );
        },
      }
    );
  };

  if (isLoading) {
    return <div className="text-gray-400">Loading integrations...</div>;
  }

  return (
    <div className="max-w-2xl space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Notification Webhooks</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-gray-400">
            Post incident created, resolved, and escalated events to a Slack or Microsoft Teams
            incoming webhook — or any endpoint that accepts a JSON payload.
          </p>

          <form onSubmit={handleSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <div className="flex-1">
              <label className="mb-1 block text-sm text-gray-400">Webhook URL</label>
              <Input
                type="url"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                placeholder="https://hooks.slack.com/services/..."
                required
              />
            </div>
            <div className="sm:w-40">
              <label className="mb-1 block text-sm text-gray-400">Type</label>
              <select
                value={webhookType}
                onChange={(e) => setWebhookType(e.target.value as WebhookType)}
                className="w-full rounded-md border border-charcoal-700 bg-charcoal-900 p-2 text-white focus:border-emerald-500 focus:outline-none"
              >
                <option value="SLACK">Slack</option>
                <option value="TEAMS">Microsoft Teams</option>
                <option value="GENERIC">Generic JSON</option>
              </select>
            </div>
            <Button type="submit" disabled={!url || createWebhook.isPending}>
              {createWebhook.isPending ? 'Adding...' : 'Add Webhook'}
            </Button>
          </form>

          {error && (
            <div className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">
              {error}
            </div>
          )}

          {webhooks && webhooks.length > 0 ? (
            <ul className="space-y-2">
              {webhooks.map((webhook) => (
                <li
                  key={webhook.id}
                  className="flex items-center justify-between gap-2 rounded-md border border-charcoal-700 bg-charcoal-900 px-3 py-2"
                >
                  <div className="flex min-w-0 items-center gap-2">
                    <Badge variant="default">{webhook.webhookType}</Badge>
                    <span className="truncate text-sm text-gray-200">{webhook.url}</span>
                  </div>
                  <button
                    type="button"
                    onClick={() => deleteWebhook.mutate({ orgId, webhookId: webhook.id })}
                    disabled={deleteWebhook.isPending}
                    className="shrink-0 rounded p-1 text-gray-400 hover:text-rose-400"
                    title="Remove webhook"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-gray-500">No webhooks configured yet.</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
