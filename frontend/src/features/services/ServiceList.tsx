
import { useServices } from '@/hooks/useServices';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/Table';
import { StatusIndicator } from '@/components/status/StatusIndicator';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Search, Plus } from 'lucide-react';
import { Input } from '@/components/ui/Input';
import { formatDistanceToNow } from 'date-fns';

export function ServiceList() {
  const { data: services, isLoading, isError } = useServices();

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
        <Button size="sm">
          <Plus className="mr-2 h-4 w-4" />
          Register Service
        </Button>
      </div>

      <div className="flex items-center justify-between space-x-4">
        <div className="relative w-72">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-500" />
          <Input placeholder="Search services..." className="pl-9" />
        </div>
        <div className="flex items-center space-x-2 text-sm text-gray-400">
          <span>{services?.length || 0} services total</span>
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
          <Button variant="outline" className="mt-4">Register Service</Button>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Service Name</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Environment</TableHead>
              <TableHead>Last Updated</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {services?.map((service) => (
              <TableRow key={service.id} className="cursor-pointer">
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
                    {service.environment}
                  </Badge>
                </TableCell>
                <TableCell className="text-gray-400">
                  {formatDistanceToNow(new Date(service.statusUpdatedAt || service.createdAt), { addSuffix: true })}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
