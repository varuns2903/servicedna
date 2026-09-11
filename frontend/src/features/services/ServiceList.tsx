
import { useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useServices, useCreateService } from '@/hooks/useServices';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/Table';
import { StatusIndicator } from '@/components/status/StatusIndicator';
import type { ServiceStatus } from '@/components/status/StatusIndicator';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Search, Plus, Download, Upload } from 'lucide-react';
import { Input } from '@/components/ui/Input';
import { formatDistanceToNow } from 'date-fns';
import { RegisterServiceModal } from './RegisterServiceModal';
import { downloadCsv, parseCsv } from '@/utils/csv';

export function ServiceList() {
  const navigate = useNavigate();
  const { data: services, isLoading, isError } = useServices();
  const createService = useCreateService();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isRegisterOpen, setRegisterOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<ServiceStatus | 'ALL'>('ALL');
  const [regionFilter, setRegionFilter] = useState<string>('ALL');
  const [importSummary, setImportSummary] = useState<string | null>(null);
  const [importing, setImporting] = useState(false);

  const regions = useMemo(
    () => [...new Set((services || []).map((s) => s.region).filter(Boolean))].sort(),
    [services]
  );

  const filteredServices = useMemo(() => {
    if (!services) return services;
    const term = search.trim().toLowerCase();
    return services.filter((service) => {
      if (statusFilter !== 'ALL' && service.status !== statusFilter) return false;
      if (regionFilter !== 'ALL' && service.region !== regionFilter) return false;
      if (
        term &&
        !service.name.toLowerCase().includes(term) &&
        !(service.description || '').toLowerCase().includes(term)
      ) {
        return false;
      }
      return true;
    });
  }, [services, search, statusFilter, regionFilter]);

  const handleExport = () => {
    const rows = (filteredServices || []).map((s) => [
      s.name,
      s.description || '',
      s.region || '',
      s.repositoryUrl || '',
      s.healthCheckUrl || '',
      s.status,
    ]);
    downloadCsv(
      'services.csv',
      ['name', 'description', 'region', 'repositoryUrl', 'healthCheckUrl', 'status'],
      rows
    );
  };

  const handleImportFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = '';

    const text = await file.text();
    const rows = parseCsv(text);
    if (rows.length < 2) {
      setImportSummary('No rows found in file.');
      return;
    }

    const header = rows[0].map((h) => h.trim().toLowerCase());
    const nameIdx = header.indexOf('name');
    if (nameIdx === -1) {
      setImportSummary('CSV must have a "name" column.');
      return;
    }
    const descIdx = header.indexOf('description');
    const regionIdx = header.indexOf('region');
    const repoIdx = header.indexOf('repositoryurl');
    const healthIdx = header.indexOf('healthcheckurl');

    setImporting(true);
    setImportSummary(null);
    let succeeded = 0;
    let failed = 0;
    for (const row of rows.slice(1)) {
      const name = row[nameIdx]?.trim();
      if (!name) continue;
      try {
        await createService.mutateAsync({
          name,
          description: descIdx >= 0 ? row[descIdx]?.trim() || undefined : undefined,
          region: regionIdx >= 0 ? row[regionIdx]?.trim() || undefined : undefined,
          repositoryUrl: repoIdx >= 0 ? row[repoIdx]?.trim() || undefined : undefined,
          healthCheckUrl: healthIdx >= 0 ? row[healthIdx]?.trim() || undefined : undefined,
        });
        succeeded++;
      } catch {
        failed++;
      }
    }
    setImporting(false);
    setImportSummary(`Imported ${succeeded} service${succeeded === 1 ? '' : 's'}${failed > 0 ? `, ${failed} failed` : ''}.`);
  };

  if (isError) {
    return (
      <div className="flex h-full flex-col">
        <h1 className="mb-6 text-2xl font-semibold tracking-tight text-white">Services</h1>
        <div className="rounded-lg border border-rose-500/20 bg-rose-500/10 p-4 text-rose-500">
          Failed to load services.
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold tracking-tight text-white">Services</h1>
        <div className="flex items-center space-x-2">
          <Button size="sm" variant="outline" onClick={handleExport} disabled={!filteredServices?.length}>
            <Download className="mr-2 h-4 w-4" />
            Export CSV
          </Button>
          <Button size="sm" variant="outline" onClick={() => fileInputRef.current?.click()} disabled={importing}>
            <Upload className="mr-2 h-4 w-4" />
            {importing ? 'Importing...' : 'Import CSV'}
          </Button>
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv,text/csv"
            className="hidden"
            onChange={handleImportFile}
          />
          <Button size="sm" onClick={() => setRegisterOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            Register Service
          </Button>
        </div>
      </div>

      {importSummary && (
        <div className="rounded-md border border-charcoal-700 bg-charcoal-800 p-2 text-sm text-gray-300">
          {importSummary}
        </div>
      )}

      <div className="flex items-center justify-between space-x-4">
        <div className="flex items-center space-x-3">
          <div className="relative w-72">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-500" />
            <Input
              placeholder="Search services..."
              className="pl-9"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as ServiceStatus | 'ALL')}
            className="bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-sm text-white focus:border-emerald-500 focus:outline-none"
          >
            <option value="ALL">All statuses</option>
            <option value="HEALTHY">Healthy</option>
            <option value="DEGRADED">Degraded</option>
            <option value="DOWN">Down</option>
            <option value="UNKNOWN">Unknown</option>
          </select>
          <select
            value={regionFilter}
            onChange={(e) => setRegionFilter(e.target.value)}
            className="bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-sm text-white focus:border-emerald-500 focus:outline-none"
          >
            <option value="ALL">All regions</option>
            {regions.map((region) => (
              <option key={region} value={region}>
                {region}
              </option>
            ))}
          </select>
        </div>
        <div className="flex items-center space-x-2 text-sm text-gray-400">
          <span>{filteredServices?.length || 0} of {services?.length || 0} services</span>
        </div>
      </div>

      {isLoading ? (
        <div className="animate-pulse space-y-4">
          <div className="h-12 w-full rounded-md bg-charcoal-700/50" />
          <div className="h-12 w-full rounded-md bg-charcoal-700/50" />
          <div className="h-12 w-full rounded-md bg-charcoal-700/50" />
        </div>
      ) : services?.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-charcoal-600 py-16 text-center">
          <div className="text-gray-400">No services registered</div>
          <p className="mt-1 text-sm text-gray-500">Register your first service to begin monitoring.</p>
          <Button variant="outline" className="mt-4" onClick={() => setRegisterOpen(true)}>
            Register Service
          </Button>
        </div>
      ) : filteredServices?.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-charcoal-600 py-16 text-center">
          <div className="text-gray-400">No services match your filters</div>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Service Name</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Region</TableHead>
              <TableHead>Last Updated</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredServices?.map((service) => (
              <TableRow
                key={service.id}
                className="cursor-pointer"
                onClick={() => navigate(`/services/${service.id}`)}
              >
                <TableCell className="font-medium text-gray-200">
                  <div className="flex flex-col">
                    <span>{service.name}</span>
                    <span className="text-xs font-normal text-gray-500">{service.description}</span>
                  </div>
                </TableCell>
                <TableCell>
                  <StatusIndicator status={service.status} size="sm" />
                </TableCell>
                <TableCell>
                  <Badge variant="default" className="uppercase tracking-wider text-[10px]">
                    {service.region || 'unassigned'}
                  </Badge>
                </TableCell>
                <TableCell className="text-gray-400">
                  {formatDistanceToNow(new Date(service.updatedAt), { addSuffix: true })}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <RegisterServiceModal open={isRegisterOpen} onClose={() => setRegisterOpen(false)} />
    </div>
  );
}
