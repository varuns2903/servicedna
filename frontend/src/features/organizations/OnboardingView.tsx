import { useState } from 'react';
import { Building2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { CreateOrganizationModal } from './CreateOrganizationModal';

export function OnboardingView() {
  const [isCreateOrgOpen, setCreateOrgOpen] = useState(false);

  return (
    <div className="flex h-screen w-screen items-center justify-center bg-charcoal-900">
      <div className="flex max-w-md flex-col items-center text-center">
        <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-emerald-500/10">
          <Building2 className="h-7 w-7 text-emerald-400" />
        </div>
        <h1 className="text-xl font-semibold text-white">Create your organization</h1>
        <p className="mt-2 text-sm text-gray-400">
          You're not a member of any organization yet. Create one to start registering services,
          tracking incidents, and inviting your team.
        </p>
        <Button className="mt-6" onClick={() => setCreateOrgOpen(true)}>
          Create Organization
        </Button>
      </div>
      <CreateOrganizationModal open={isCreateOrgOpen} onClose={() => setCreateOrgOpen(false)} />
    </div>
  );
}
