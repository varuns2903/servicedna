
import { useDashboard } from '@/hooks/useDashboard';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Skeleton } from '@/components/ui/Skeleton';
import { StatusIndicator } from '@/components/status/StatusIndicator';

export function Dashboard() {
  const { data: dashboard, isLoading, isError } = useDashboard();

  if (isError) {
    return (
      <div className="flex h-full flex-col">
        <h1 className="mb-6 text-2xl font-semibold tracking-tight text-white">Dashboard</h1>
        <div className="rounded-lg border border-rose-500/20 bg-rose-500/10 p-4 text-rose-500">
          Failed to load dashboard data.
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col space-y-6">
      <h1 className="text-2xl font-semibold tracking-tight text-white">Dashboard</h1>

      {/* Summary Cards */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-400">Total Services</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading ? <Skeleton className="h-8 w-16" /> : <div className="text-3xl font-bold text-white">{dashboard?.totalServices}</div>}
          </CardContent>
        </Card>
        
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-400">Healthy</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading ? <Skeleton className="h-8 w-16" /> : <div className="text-3xl font-bold text-emerald-400">{dashboard?.healthyServices}</div>}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-400">Degraded</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading ? <Skeleton className="h-8 w-16" /> : <div className="text-3xl font-bold text-amber-400">{dashboard?.degradedServices}</div>}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-400">Down</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading ? <Skeleton className="h-8 w-16" /> : <div className="text-3xl font-bold text-rose-500">{dashboard?.downServices}</div>}
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Services Overview */}
        <Card>
          <CardHeader>
            <CardTitle>Service Status</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="space-y-4">
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
              </div>
            ) : dashboard?.servicesOverview.length === 0 ? (
              <div className="text-sm text-gray-400">No services registered.</div>
            ) : (
              <div className="space-y-4">
                {dashboard?.servicesOverview.map((svc) => (
                  <div key={svc.id} className="flex items-center justify-between border-b border-charcoal-700 pb-4 last:border-0 last:pb-0">
                    <div className="font-medium">{svc.name}</div>
                    <div className="flex items-center space-x-6">
                      <div className="w-16 text-right text-sm text-gray-400">
                        {svc.latencyMs ? `${svc.latencyMs} ms` : '—'}
                      </div>
                      <div className="w-32">
                        <StatusIndicator status={svc.status} size="sm" />
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Active Incidents */}
        <Card>
          <CardHeader>
            <CardTitle>Active Incidents</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="space-y-4">
                <Skeleton className="h-10 w-full" />
              </div>
            ) : dashboard?.activeIncidents.length === 0 ? (
              <div className="text-sm text-gray-400">No active incidents.</div>
            ) : (
              <div className="space-y-4">
                {dashboard?.activeIncidents.map((incident) => (
                  <div key={incident.id} className="flex flex-col space-y-1 rounded-md border border-charcoal-700 bg-charcoal-800/50 p-3">
                    <div className="flex items-center justify-between">
                      <span className="font-medium text-rose-400">{incident.title}</span>
                      <span className="text-xs text-gray-500">{new Date(incident.createdAt).toLocaleTimeString()}</span>
                    </div>
                    <div className="flex items-center space-x-2 text-xs">
                      <span className="rounded bg-charcoal-700 px-1.5 py-0.5 text-gray-300">{incident.severity}</span>
                      <span className="text-gray-400">{incident.status}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
