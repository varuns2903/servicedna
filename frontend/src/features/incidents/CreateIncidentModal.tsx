import { useState } from 'react';
import axios from 'axios';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useCreateIncident } from '@/hooks/useIncidents';
import { useServices } from '@/hooks/useServices';
import type { IncidentSeverity } from '@/api/incidents.api';

interface CreateIncidentModalProps {
  open: boolean;
  onClose: () => void;
  orgId: string;
}

export function CreateIncidentModal({ open, onClose, orgId }: CreateIncidentModalProps) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [severity, setSeverity] = useState<IncidentSeverity>('MAJOR');
  const [affectedServiceIds, setAffectedServiceIds] = useState<string[]>([]);

  const { data: services } = useServices();
  const createIncident = useCreateIncident();

  const reset = () => {
    setTitle('');
    setDescription('');
    setSeverity('MAJOR');
    setAffectedServiceIds([]);
    createIncident.reset();
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  const toggleService = (serviceId: string) => {
    setAffectedServiceIds((current) =>
      current.includes(serviceId)
        ? current.filter((id) => id !== serviceId)
        : [...current, serviceId]
    );
  };

  const isValid = title.trim().length >= 5 && description.trim().length > 0;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!isValid) return;

    createIncident.mutate(
      {
        orgId,
        data: {
          title: title.trim(),
          description: description.trim(),
          severity,
          affectedServiceIds,
        },
      },
      { onSuccess: handleClose }
    );
  };

  return (
    <Modal open={open} onClose={handleClose} title="Report Incident">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="mb-1 block text-sm text-gray-400">Title</label>
          <Input
            autoFocus
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Checkout service returning 500s"
            minLength={5}
            maxLength={255}
            required
          />
        </div>
        <div>
          <label className="mb-1 block text-sm text-gray-400">Description</label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="What's happening, what's the impact, what's known so far..."
            rows={4}
            required
            className="w-full rounded-md border border-charcoal-700 bg-charcoal-900 p-2 text-sm text-white placeholder:text-gray-500 focus:border-emerald-500 focus:outline-none"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm text-gray-400">Severity</label>
          <select
            value={severity}
            onChange={(e) => setSeverity(e.target.value as IncidentSeverity)}
            className="w-full rounded-md border border-charcoal-700 bg-charcoal-900 p-2 text-sm text-white focus:border-emerald-500 focus:outline-none"
          >
            <option value="CRITICAL">Critical</option>
            <option value="MAJOR">Major</option>
            <option value="MINOR">Minor</option>
            <option value="LOW">Low</option>
          </select>
        </div>
        <div>
          <label className="mb-1 block text-sm text-gray-400">Affected Services</label>
          {!services || services.length === 0 ? (
            <p className="text-xs text-gray-500">No services registered yet.</p>
          ) : (
            <div className="max-h-40 space-y-1 overflow-y-auto rounded-md border border-charcoal-700 bg-charcoal-900 p-2">
              {services.map((service) => (
                <label
                  key={service.id}
                  className="flex items-center space-x-2 rounded px-1.5 py-1 text-sm text-gray-300 hover:bg-charcoal-800"
                >
                  <input
                    type="checkbox"
                    checked={affectedServiceIds.includes(service.id)}
                    onChange={() => toggleService(service.id)}
                    className="rounded border-charcoal-600 bg-charcoal-900 text-emerald-500 focus:ring-emerald-500"
                  />
                  <span>{service.name}</span>
                </label>
              ))}
            </div>
          )}
        </div>

        {createIncident.isError && (
          <div className="rounded-md border border-rose-500/20 bg-rose-500/10 p-2 text-xs text-rose-400">
            {(axios.isAxiosError(createIncident.error) && createIncident.error.response?.data?.message) ||
              'Failed to create incident. Please try again.'}
          </div>
        )}

        <div className="flex justify-end space-x-2">
          <Button type="button" variant="outline" onClick={handleClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={!isValid || createIncident.isPending}>
            {createIncident.isPending ? 'Creating...' : 'Create Incident'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
