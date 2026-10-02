import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import { CheckCircle2, Loader2, XCircle } from 'lucide-react';
import { GitHubAppApi } from '@/api/githubApp.api';
import { useOrganizationStore } from '@/stores/useOrganizationStore';

/** Where GitHub returns after the app is installed: links the installation to this organization. */
export function GitHubSetup() {
  const [search] = useSearchParams();
  const orgId = useOrganizationStore((s) => s.selectedOrganizationId);
  const installationId = Number(search.get('installation_id'));
  const code = search.get('code');
  const setupState = search.get('state');
  const complete = !!installationId && !!code && !!setupState;
  const [result, setState] = useState<{ status: 'working' | 'done' | 'error'; message: string }>({ status: 'working', message: 'Connecting…' });
  const started = useRef(false);
  const state = complete
    ? result
    : { status: 'error' as const, message: 'GitHub didn’t send the installation details back. Start again from Settings → Integrations.' };

  useEffect(() => {
    if (started.current || !orgId || !complete) return;
    started.current = true;
    GitHubAppApi.connect(orgId, { installationId, code: code!, state: setupState! })
      .then((i) => setState({ status: 'done', message: `Connected ${i.account}. Repositories with a servicedna.yaml are syncing now.` }))
      .catch((err) =>
        setState({ status: 'error', message: (axios.isAxiosError(err) && err.response?.data?.message) || 'Couldn’t connect the installation.' }),
      );
  }, [orgId, complete, installationId, code, setupState]);

  return (
    <div className="mx-auto max-w-lg p-8">
      <div className="flex items-start gap-3 rounded-lg border border-charcoal-700 bg-charcoal-900 p-5" data-testid="github-setup">
        {state.status === 'working' && <Loader2 className="h-5 w-5 shrink-0 animate-spin text-gray-400" />}
        {state.status === 'done' && <CheckCircle2 className="h-5 w-5 shrink-0 text-emerald-400" />}
        {state.status === 'error' && <XCircle className="h-5 w-5 shrink-0 text-rose-400" />}
        <div>
          <p className="text-sm text-gray-100">{state.message}</p>
          {state.status !== 'working' && (
            <Link to="/settings" className="mt-2 inline-block text-sm text-emerald-400 hover:underline">
              Back to Settings
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}
