import { useParams } from 'react-router-dom';
import { usePublicStatus } from '@/hooks/usePublicStatus';
import { Badge } from '@/components/ui/Badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { CheckCircle, AlertTriangle, XCircle, Activity } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import type { ServiceStatus } from '@/components/status/StatusIndicator';

export function PublicStatusPage() {
  const { orgId } = useParams<{ orgId: string }>();
  const { data, isLoading, isError } = usePublicStatus(orgId);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-charcoal-900 flex items-center justify-center">
        <div className="text-gray-400 animate-pulse">Loading status...</div>
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="min-h-screen bg-charcoal-900 flex items-center justify-center">
        <div className="text-rose-500 flex flex-col items-center space-y-2">
          <AlertTriangle className="h-8 w-8" />
          <p>Failed to load status page or organization not found.</p>
        </div>
      </div>
    );
  }

  const getStatusIcon = (status: ServiceStatus) => {
    switch (status) {
      case 'HEALTHY':
        return <CheckCircle className="h-5 w-5 text-emerald-500" />;
      case 'DEGRADED':
        return <AlertTriangle className="h-5 w-5 text-amber-500" />;
      case 'DOWN':
        return <XCircle className="h-5 w-5 text-rose-500" />;
    }
  };

  const getOverallStateBanner = () => {
    switch (data.overallState) {
      case 'OPERATIONAL':
        return (
          <div className="bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 p-6 rounded-lg flex items-center space-x-4">
            <CheckCircle className="h-8 w-8" />
            <div>
              <h2 className="text-xl font-semibold">All Systems Operational</h2>
              <p className="text-emerald-400/80">No active incidents reported.</p>
            </div>
          </div>
        );
      case 'DEGRADED':
        return (
          <div className="bg-amber-500/10 border border-amber-500/20 text-amber-400 p-6 rounded-lg flex items-center space-x-4">
            <AlertTriangle className="h-8 w-8" />
            <div>
              <h2 className="text-xl font-semibold">Partial System Outage</h2>
              <p className="text-amber-400/80">Some services are experiencing issues.</p>
            </div>
          </div>
        );
      case 'OUTAGE':
        return (
          <div className="bg-rose-500/10 border border-rose-500/20 text-rose-500 p-6 rounded-lg flex items-center space-x-4">
            <XCircle className="h-8 w-8" />
            <div>
              <h2 className="text-xl font-semibold">Major System Outage</h2>
              <p className="text-rose-500/80">We are actively investigating a major platform outage.</p>
            </div>
          </div>
        );
    }
  };

  return (
    <div className="min-h-screen bg-charcoal-900 text-gray-200">
      <div className="max-w-4xl mx-auto px-4 py-12 space-y-12">
        {/* Header */}
        <div className="flex items-center space-x-3">
          <Activity className="h-8 w-8 text-emerald-500" />
          <h1 className="text-3xl font-bold text-white">{data.organizationName} Status</h1>
        </div>

        {/* Global Status Banner */}
        {getOverallStateBanner()}

        {/* Active Incidents */}
        {data.activeIncidents.length > 0 && (
          <div className="space-y-4">
            <h2 className="text-2xl font-semibold text-white border-b border-charcoal-700 pb-2">Active Incidents</h2>
            <div className="space-y-4">
              {data.activeIncidents.map((incident) => (
                <Card key={incident.id} className="border-rose-500/20">
                  <CardHeader className="pb-2">
                    <div className="flex items-center justify-between">
                      <CardTitle className="text-lg">{incident.title}</CardTitle>
                      <Badge variant={incident.status === 'RESOLVED' ? 'success' : 'warning'}>
                        {incident.status}
                      </Badge>
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <p className="text-gray-300">{incident.description}</p>
                    <div className="text-sm text-gray-500">
                      Reported {formatDistanceToNow(new Date(incident.createdAt), { addSuffix: true })}
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          </div>
        )}

        {/* Services List */}
        <div className="space-y-4">
          <h2 className="text-2xl font-semibold text-white border-b border-charcoal-700 pb-2">Service Status</h2>
          <div className="bg-charcoal-800 rounded-lg border border-charcoal-700 divide-y divide-charcoal-700">
            {data.services.map((service) => (
              <div key={service.id} className="p-4 flex items-center justify-between hover:bg-charcoal-800/80 transition-colors">
                <div>
                  <h3 className="font-medium text-white">{service.name}</h3>
                  <p className="text-sm text-gray-500">{service.description}</p>
                </div>
                <div className="flex items-center space-x-2">
                  <span className="text-sm font-medium capitalize text-gray-400">
                    {service.status.toLowerCase()}
                  </span>
                  {getStatusIcon(service.status)}
                </div>
              </div>
            ))}
            {data.services.length === 0 && (
              <div className="p-8 text-center text-gray-500">
                No services are publicly listed yet.
              </div>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="text-center pt-12 pb-4 text-sm text-gray-500">
          Powered by <span className="text-emerald-500 font-semibold">ServiceDNA</span>
        </div>
      </div>
    </div>
  );
}
