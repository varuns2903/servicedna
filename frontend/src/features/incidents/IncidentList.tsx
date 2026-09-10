import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { formatDistanceToNow } from 'date-fns';
import { useIncidents } from '@/hooks/useIncidents';
import { useOrganizationStore } from '@/stores/useOrganizationStore';
import { Card, CardContent } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Input } from '@/components/ui/Input';
import { CheckCircle, Clock, Search } from 'lucide-react';
import type { IncidentStatus, IncidentSeverity } from '@/api/incidents.api';

export function IncidentList() {
  const currentOrgId = useOrganizationStore((state) => state.selectedOrganizationId);
  const { data: incidents, isLoading } = useIncidents(currentOrgId || undefined);

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<IncidentStatus | 'ALL'>('ALL');
  const [severityFilter, setSeverityFilter] = useState<IncidentSeverity | 'ALL'>('ALL');

  const filteredIncidents = useMemo(() => {
    if (!incidents) return incidents;
    const term = search.trim().toLowerCase();
    return incidents.filter((incident) => {
      if (statusFilter !== 'ALL' && incident.status !== statusFilter) return false;
      if (severityFilter !== 'ALL' && incident.severity !== severityFilter) return false;
      if (term && !incident.title.toLowerCase().includes(term) && !incident.description.toLowerCase().includes(term)) {
        return false;
      }
      return true;
    });
  }, [incidents, search, statusFilter, severityFilter]);

  if (isLoading) {
    return <div className="p-8 text-gray-400">Loading incidents...</div>;
  }

  return (
    <div className="p-8 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-white">Incidents</h1>
      </div>

      <div className="flex items-center space-x-4">
        <div className="relative w-72">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-500" />
          <Input
            placeholder="Search incidents..."
            className="pl-9"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as IncidentStatus | 'ALL')}
          className="bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-sm text-white focus:border-emerald-500 focus:outline-none"
        >
          <option value="ALL">All statuses</option>
          <option value="INVESTIGATING">Investigating</option>
          <option value="IDENTIFIED">Identified</option>
          <option value="MONITORING">Monitoring</option>
          <option value="RESOLVED">Resolved</option>
        </select>
        <select
          value={severityFilter}
          onChange={(e) => setSeverityFilter(e.target.value as IncidentSeverity | 'ALL')}
          className="bg-charcoal-900 border border-charcoal-700 rounded-md p-2 text-sm text-white focus:border-emerald-500 focus:outline-none"
        >
          <option value="ALL">All severities</option>
          <option value="CRITICAL">Critical</option>
          <option value="MAJOR">Major</option>
          <option value="MINOR">Minor</option>
          <option value="LOW">Low</option>
        </select>
        <span className="text-sm text-gray-400">{filteredIncidents?.length || 0} incidents</span>
      </div>

      <div className="grid gap-4">
        {filteredIncidents?.length === 0 ? (
          <Card>
            <CardContent className="p-8 text-center text-gray-400">
              <CheckCircle className="mx-auto h-8 w-8 text-emerald-500 mb-2" />
              <p>
                {incidents?.length === 0
                  ? 'No incidents reported. Everything is running smoothly.'
                  : 'No incidents match your filters.'}
              </p>
            </CardContent>
          </Card>
        ) : (
          filteredIncidents?.map((incident) => (
            <Link key={incident.id} to={`/incidents/${incident.id}`} className="block">
              <Card className="hover:border-charcoal-600 transition-colors cursor-pointer">
                <CardContent className="p-6">
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="flex items-center space-x-3 mb-2">
                        <h3 className="text-lg font-medium text-white">{incident.title}</h3>
                        <Badge variant={incident.severity === 'CRITICAL' ? 'danger' : incident.severity === 'MAJOR' ? 'warning' : 'default'}>
                          {incident.severity}
                        </Badge>
                        <Badge variant={incident.status === 'RESOLVED' ? 'success' : 'warning'}>
                          {incident.status}
                        </Badge>
                      </div>
                      <p className="text-gray-400 line-clamp-2">{incident.description}</p>
                    </div>
                    <div className="text-right text-sm text-gray-500">
                      <div className="flex items-center justify-end space-x-1">
                        <Clock className="h-4 w-4" />
                        <span>{formatDistanceToNow(new Date(incident.createdAt), { addSuffix: true })}</span>
                      </div>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </Link>
          ))
        )}
      </div>
    </div>
  );
}
