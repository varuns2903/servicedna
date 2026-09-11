import { useEffect, useState } from 'react';
import axios from 'axios';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useEscalationPolicy, useUpsertEscalationPolicy } from '@/hooks/useEscalation';

interface EscalationTabProps {
  orgId: string;
}

export function EscalationTab({ orgId }: EscalationTabProps) {
  const { data: policy, isLoading } = useEscalationPolicy(orgId);
  const upsertPolicy = useUpsertEscalationPolicy();

  const [escalationEmail, setEscalationEmail] = useState('');
  const [escalateAfterMinutes, setEscalateAfterMinutes] = useState(15);

  useEffect(() => {
    if (policy) {
      setEscalationEmail(policy.escalationEmail || '');
      setEscalateAfterMinutes(policy.escalateAfterMinutes);
    }
  }, [policy]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    upsertPolicy.mutate({ orgId, request: { escalationEmail, escalateAfterMinutes } });
  };

  if (isLoading) {
    return <div className="text-gray-400">Loading escalation policy...</div>;
  }

  return (
    <div className="max-w-2xl">
      <Card>
        <CardHeader>
          <CardTitle>Escalation Policy</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="mb-4 text-sm text-gray-400">
            CRITICAL and MAJOR incidents page whoever's currently on call. If nobody acknowledges
            it within this window, it escalates here too.
          </p>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="mb-1 block text-sm text-gray-400">Escalation Email</label>
              <Input
                type="email"
                value={escalationEmail}
                onChange={(e) => setEscalationEmail(e.target.value)}
                placeholder="oncall-backup@example.com"
                required
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-gray-400">Escalate After (minutes)</label>
              <input
                type="number"
                min={1}
                value={escalateAfterMinutes}
                onChange={(e) => setEscalateAfterMinutes(Number(e.target.value))}
                className="w-full rounded-md border border-charcoal-700 bg-charcoal-900 p-2 text-white focus:border-emerald-500 focus:outline-none"
              />
            </div>

            {upsertPolicy.isError && (
              <div className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">
                {(axios.isAxiosError(upsertPolicy.error) && upsertPolicy.error.response?.data?.message) ||
                  'Failed to save escalation policy.'}
              </div>
            )}
            {upsertPolicy.isSuccess && (
              <div className="rounded-md border border-emerald-500/20 bg-emerald-500/10 p-2 text-xs text-emerald-400">
                Saved.
              </div>
            )}

            <div className="flex justify-end">
              <Button type="submit" disabled={!escalationEmail || upsertPolicy.isPending}>
                {upsertPolicy.isPending ? 'Saving...' : 'Save Policy'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
