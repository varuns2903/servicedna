import { useEffect, useState } from 'react';
import axios from 'axios';
import { Copy, Check, AlertTriangle } from 'lucide-react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useUpdateService, useDeleteService, useRegenerateApiKey } from '@/hooks/useServices';
import type { ServiceDto } from '@/api/services.api';

interface EditServiceModalProps {
  open: boolean;
  onClose: () => void;
  service: ServiceDto | null;
}

export function EditServiceModal({ open, onClose, service }: EditServiceModalProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [repositoryUrl, setRepositoryUrl] = useState('');
  const [region, setRegion] = useState('');
  const [healthCheckUrl, setHealthCheckUrl] = useState('');
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [newApiKey, setNewApiKey] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const updateService = useUpdateService();
  const deleteService = useDeleteService();
  const regenerateApiKey = useRegenerateApiKey();

  useEffect(() => {
    if (service) {
      setName(service.name);
      setDescription(service.description || '');
      setRepositoryUrl(service.repositoryUrl || '');
      setRegion(service.region || '');
      setHealthCheckUrl(service.healthCheckUrl || '');
    }
  }, [service]);

  const reset = () => {
    setConfirmingDelete(false);
    setNewApiKey(null);
    setCopied(false);
    updateService.reset();
    deleteService.reset();
    regenerateApiKey.reset();
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!service || name.trim().length < 3) return;

    updateService.mutate(
      {
        serviceId: service.id,
        request: {
          name: name.trim(),
          description: description.trim() || undefined,
          repositoryUrl: repositoryUrl.trim() || undefined,
          region: region.trim() || undefined,
          healthCheckUrl: healthCheckUrl.trim() || undefined,
        },
      },
      { onSuccess: handleClose }
    );
  };

  const handleDelete = () => {
    if (!service) return;
    deleteService.mutate(service.id, { onSuccess: handleClose });
  };

  const handleRegenerate = () => {
    if (!service) return;
    regenerateApiKey.mutate(service.id, {
      onSuccess: (updated) => setNewApiKey(updated.apiKey),
    });
  };

  const handleCopyKey = async () => {
    if (!newApiKey) return;
    await navigator.clipboard.writeText(newApiKey);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (!service) return null;

  return (
    <Modal open={open} onClose={handleClose} title="Edit Service">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="mb-1 block text-sm text-gray-400">Service Name</label>
          <Input
            autoFocus
            value={name}
            onChange={(e) => setName(e.target.value)}
            minLength={3}
            maxLength={255}
            required
          />
        </div>
        <div>
          <label className="mb-1 block text-sm text-gray-400">Description</label>
          <Input value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>
        <div>
          <label className="mb-1 block text-sm text-gray-400">Repository URL</label>
          <Input value={repositoryUrl} onChange={(e) => setRepositoryUrl(e.target.value)} />
        </div>
        <div>
          <label className="mb-1 block text-sm text-gray-400">Region</label>
          <Input value={region} onChange={(e) => setRegion(e.target.value)} />
        </div>
        <div>
          <label className="mb-1 block text-sm text-gray-400">Health Check URL</label>
          <Input
            type="url"
            value={healthCheckUrl}
            onChange={(e) => setHealthCheckUrl(e.target.value)}
            placeholder="https://api.example.com/health"
          />
        </div>

        {updateService.isError && (
          <div className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">
            {(axios.isAxiosError(updateService.error) && updateService.error.response?.data?.message) ||
              'Failed to save changes. Please try again.'}
          </div>
        )}

        <div className="flex justify-end space-x-2">
          <Button type="button" variant="outline" onClick={handleClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={name.trim().length < 3 || updateService.isPending}>
            {updateService.isPending ? 'Saving...' : 'Save Changes'}
          </Button>
        </div>
      </form>

      <div className="mt-6 space-y-3 border-t border-charcoal-700 pt-5">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-sm text-gray-300">API key</p>
            <p className="text-xs text-gray-500">Rotate it if it's ever been exposed.</p>
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleRegenerate}
            disabled={regenerateApiKey.isPending}
          >
            {regenerateApiKey.isPending ? 'Regenerating...' : 'Regenerate Key'}
          </Button>
        </div>

        {newApiKey && (
          <div className="flex items-center space-x-2 rounded-md border border-charcoal-700 bg-charcoal-900 p-3">
            <code className="flex-1 truncate text-xs text-emerald-400">{newApiKey}</code>
            <button
              type="button"
              onClick={handleCopyKey}
              className="rounded-md p-1.5 text-gray-400 transition-colors hover:bg-charcoal-700 hover:text-white"
              title="Copy API key"
            >
              {copied ? <Check className="h-4 w-4 text-emerald-400" /> : <Copy className="h-4 w-4" />}
            </button>
          </div>
        )}
        {newApiKey && (
          <p className="text-xs text-amber-400">
            This won't be shown again — copy it now. The old key stops working immediately.
          </p>
        )}
      </div>

      <div className="mt-6 space-y-3 border-t border-charcoal-700 pt-5">
        {!confirmingDelete ? (
          <button
            type="button"
            onClick={() => setConfirmingDelete(true)}
            className="text-sm text-rose-400 transition-colors hover:text-rose-300"
          >
            Delete this service
          </button>
        ) : (
          <div className="space-y-3 rounded-md border border-rose-500/30 bg-rose-500/10 p-3">
            <div className="flex items-start space-x-2">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-rose-400" />
              <p className="text-xs text-rose-300">
                This permanently deletes <span className="font-medium">{service.name}</span>,
                its dependency links, alert rules, and ping history. This can't be undone.
              </p>
            </div>
            {deleteService.isError && (
              <p className="text-xs text-rose-400">
                {(axios.isAxiosError(deleteService.error) && deleteService.error.response?.data?.message) ||
                  'Failed to delete service.'}
              </p>
            )}
            <div className="flex justify-end space-x-2">
              <Button type="button" variant="outline" size="sm" onClick={() => setConfirmingDelete(false)}>
                Cancel
              </Button>
              <Button type="button" variant="danger" size="sm" onClick={handleDelete} disabled={deleteService.isPending}>
                {deleteService.isPending ? 'Deleting...' : 'Delete Permanently'}
              </Button>
            </div>
          </div>
        )}
      </div>
    </Modal>
  );
}
