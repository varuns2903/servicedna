import { Link } from 'react-router-dom';
import { formatDistanceToNow } from 'date-fns';
import { useIncidents } from '@/hooks/useIncidents';
import { useOrganizationStore } from '@/stores/useOrganizationStore';
import { Card, CardContent } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { CheckCircle, Clock } from 'lucide-react';

export function IncidentList() {
  const currentOrgId = useOrganizationStore((state) => state.selectedOrganizationId);
  const { data: incidents, isLoading } = useIncidents(currentOrgId || undefined);

  if (isLoading) {
    return <div className="p-8 text-gray-400">Loading incidents...</div>;
  }

  return (
    <div className="p-8 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-white">Incidents</h1>
      </div>

      <div className="grid gap-4">
        {incidents?.length === 0 ? (
          <Card>
            <CardContent className="p-8 text-center text-gray-400">
              <CheckCircle className="mx-auto h-8 w-8 text-emerald-500 mb-2" />
              <p>No incidents reported. Everything is running smoothly.</p>
            </CardContent>
          </Card>
        ) : (
          incidents?.map((incident) => (
            <Link key={incident.id} to={`/incidents/${incident.id}`} className="block">
              <Card className="hover:border-charcoal-600 transition-colors cursor-pointer">
                <CardContent className="p-6">
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="flex items-center space-x-3 mb-2">
                        <h3 className="text-lg font-medium text-white">{incident.title}</h3>
                        <Badge variant={incident.severity === 'SEV1' ? 'danger' : incident.severity === 'SEV2' ? 'warning' : 'default'}>
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
