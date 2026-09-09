import { useState } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useCreateOrganization } from '@/hooks/useOrganizations';

interface CreateOrganizationModalProps {
  open: boolean;
  onClose: () => void;
}

export function CreateOrganizationModal({ open, onClose }: CreateOrganizationModalProps) {
  const [name, setName] = useState('');
  const createOrganization = useCreateOrganization();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (name.trim().length < 3) return;

    createOrganization.mutate(name.trim(), {
      onSuccess: () => {
        setName('');
        onClose();
      }
    });
  };

  const handleClose = () => {
    createOrganization.reset();
    setName('');
    onClose();
  };

  return (
    <Modal open={open} onClose={handleClose} title="Create Organization">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="mb-1 block text-sm text-gray-400">Organization Name</label>
          <Input
            autoFocus
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Acme Inc."
            minLength={3}
            maxLength={255}
            required
          />
          <p className="mt-1 text-xs text-gray-500">3-255 characters.</p>
        </div>

        {createOrganization.isError && (
          <div className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">
            Failed to create organization. Please try again.
          </div>
        )}

        <div className="flex justify-end space-x-2">
          <Button type="button" variant="outline" onClick={handleClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={name.trim().length < 3 || createOrganization.isPending}>
            {createOrganization.isPending ? 'Creating...' : 'Create Organization'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
