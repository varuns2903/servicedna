import { useState } from 'react';
import axios from 'axios';
import { Copy, Check } from 'lucide-react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useCreateService } from '@/hooks/useServices';
import type { ServiceDto } from '@/api/services.api';

interface RegisterServiceModalProps {
  open: boolean;
  onClose: () => void;
}

export function RegisterServiceModal({ open, onClose }: RegisterServiceModalProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [repositoryUrl, setRepositoryUrl] = useState('');
  const [region, setRegion] = useState('');
  const [createdService, setCreatedService] = useState<(ServiceDto & { apiKey: string }) | null>(null);
  const [copied, setCopied] = useState(false);
  const createService = useCreateService();

  const reset = () => {
    setName('');
    setDescription('');
    setRepositoryUrl('');
    setRegion('');
    setCreatedService(null);
    setCopied(false);
    createService.reset();
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (name.trim().length < 3) return;

    createService.mutate(
      {
        name: name.trim(),
        description: description.trim() || undefined,
        repositoryUrl: repositoryUrl.trim() || undefined,
        region: region.trim() || undefined,
      },
      { onSuccess: (service) => setCreatedService(service) }
    );
  };

  const handleCopyKey = async () => {
    if (!createdService) return;
    await navigator.clipboard.writeText(createdService.apiKey);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (createdService) {
    return (
      <Modal open={open} onClose={handleClose} title="Service Registered">
        <div className="space-y-4">
          <p className="text-sm text-gray-300">
            <span className="font-medium text-white">{createdService.name}</span> is registered.
            Use this API key to send health pings from the service to{' '}
            <code className="rounded bg-charcoal-900 px-1 py-0.5 text-xs">POST /api/v1/telemetry/ping</code>.
          </p>
          <div className="flex items-center space-x-2 rounded-md border border-charcoal-700 bg-charcoal-900 p-3">
            <code className="flex-1 truncate text-xs text-emerald-400">{createdService.apiKey}</code>
            <button
              onClick={handleCopyKey}
              className="rounded-md p-1.5 text-gray-400 transition-colors hover:bg-charcoal-700 hover:text-white"
              title="Copy API key"
            >
              {copied ? <Check className="h-4 w-4 text-emerald-400" /> : <Copy className="h-4 w-4" />}
            </button>
          </div>
          <div className="flex justify-end">
            <Button onClick={handleClose}>Done</Button>
          </div>
        </div>
      </Modal>
    );
  }

  return (
    <Modal open={open} onClose={handleClose} title="Register Service">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="mb-1 block text-sm text-gray-400">Service Name</label>
          <Input
            autoFocus
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="payments-api"
            minLength={3}
            maxLength={255}
            required
          />
        </div>
        <div>
          <label className="mb-1 block text-sm text-gray-400">Description</label>
          <Input
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Handles payment processing"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm text-gray-400">Repository URL</label>
          <Input
            value={repositoryUrl}
            onChange={(e) => setRepositoryUrl(e.target.value)}
            placeholder="https://github.com/org/repo"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm text-gray-400">Region</label>
          <Input
            value={region}
            onChange={(e) => setRegion(e.target.value)}
            placeholder="us-east-1"
          />
        </div>

        {createService.isError && (
          <div className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">
            {(axios.isAxiosError(createService.error) && createService.error.response?.data?.message) ||
              'Failed to register service. Please try again.'}
          </div>
        )}

        <div className="flex justify-end space-x-2">
          <Button type="button" variant="outline" onClick={handleClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={name.trim().length < 3 || createService.isPending}>
            {createService.isPending ? 'Registering...' : 'Register Service'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
