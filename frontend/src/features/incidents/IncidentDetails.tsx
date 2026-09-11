import { useParams, Link } from 'react-router-dom';
import {
  useIncident,
  useIncidentPostMortem,
  useUpdateIncidentStatus,
  useUpsertPostMortem,
  useAcknowledgeIncident,
} from '@/hooks/useIncidents';
import { useOrganizationStore } from '@/stores/useOrganizationStore';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { ArrowLeft, Save, CheckCheck } from 'lucide-react';
import { useState, useEffect } from 'react';
import type { IncidentStatus } from '@/api/incidents.api';

export function IncidentDetails() {
  const { id } = useParams<{ id: string }>();
  const currentOrgId = useOrganizationStore((state) => state.selectedOrganizationId);
  const { data: incident, isLoading } = useIncident(currentOrgId || undefined, id);
  const { data: postMortem } = useIncidentPostMortem(currentOrgId || undefined, id);

  const updateStatus = useUpdateIncidentStatus();
  const upsertPostMortem = useUpsertPostMortem();
  const acknowledgeIncident = useAcknowledgeIncident();

  const [pmContent, setPmContent] = useState('');

  useEffect(() => {
    if (postMortem) {
      setPmContent(postMortem.content);
    }
  }, [postMortem]);

  if (isLoading || !incident) {
    return <div className="p-8 text-gray-400">Loading incident...</div>;
  }

  const handleStatusChange = (newStatus: IncidentStatus) => {
    if (currentOrgId && id) {
      updateStatus.mutate({ orgId: currentOrgId, incidentId: id, data: { status: newStatus } });
    }
  };

  const handleSavePostMortem = () => {
    if (currentOrgId && id) {
      upsertPostMortem.mutate({ orgId: currentOrgId, incidentId: id, data: { content: pmContent } });
    }
  };

  const handleAcknowledge = () => {
    if (currentOrgId && id) {
      acknowledgeIncident.mutate({ orgId: currentOrgId, incidentId: id });
    }
  };

  return (
    <div className="p-4 space-y-6 md:p-8">
      <div className="flex flex-wrap items-center gap-3">
        <Link to="/incidents" className="text-gray-400 hover:text-white transition-colors">
          <ArrowLeft className="h-5 w-5" />
        </Link>
        <h1 className="text-xl font-semibold text-white sm:text-2xl">Incident: {incident.title}</h1>
        <Badge variant={incident.severity === 'CRITICAL' ? 'danger' : incident.severity === 'MAJOR' ? 'warning' : 'default'}>
          {incident.severity}
        </Badge>
        <Badge variant={incident.status === 'RESOLVED' ? 'success' : 'warning'}>
          {incident.status}
        </Badge>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card>
            <CardHeader>
              <CardTitle>Description</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-gray-300 whitespace-pre-wrap">{incident.description}</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Post-Mortem</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <textarea
                value={pmContent}
                onChange={(e) => setPmContent(e.target.value)}
                placeholder="Write the post-mortem analysis here..."
                className="w-full h-48 bg-charcoal-900 border border-charcoal-700 rounded-md p-3 text-gray-100 focus:outline-none focus:border-emerald-500 transition-colors"
              />
              <div className="flex justify-end">
                <Button 
                  onClick={handleSavePostMortem} 
                  disabled={upsertPostMortem.isPending}
                  className="flex items-center space-x-2"
                >
                  <Save className="h-4 w-4" />
                  <span>{upsertPostMortem.isPending ? 'Saving...' : 'Save Post-Mortem'}</span>
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Actions</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {incident.acknowledgedAt ? (
                <div className="flex items-center space-x-2 rounded-md border border-emerald-500/20 bg-emerald-500/10 p-2 text-xs text-emerald-400">
                  <CheckCheck className="h-4 w-4 shrink-0" />
                  <span>Acknowledged {new Date(incident.acknowledgedAt).toLocaleString()}</span>
                </div>
              ) : (
                <Button
                  variant="outline"
                  className="w-full justify-start"
                  onClick={handleAcknowledge}
                  disabled={acknowledgeIncident.isPending}
                >
                  <CheckCheck className="mr-2 h-4 w-4" />
                  {acknowledgeIncident.isPending ? 'Acknowledging...' : 'Acknowledge'}
                </Button>
              )}
              {incident.escalatedAt && (
                <div className="rounded-md border border-amber-500/20 bg-amber-500/10 p-2 text-xs text-amber-400">
                  Escalated {new Date(incident.escalatedAt).toLocaleString()}
                </div>
              )}
              <Button
                variant="outline"
                className="w-full justify-start"
                onClick={() => handleStatusChange('INVESTIGATING')}
                disabled={incident.status === 'INVESTIGATING'}
              >
                Set to Investigating
              </Button>
              <Button 
                variant="outline" 
                className="w-full justify-start"
                onClick={() => handleStatusChange('IDENTIFIED')}
                disabled={incident.status === 'IDENTIFIED'}
              >
                Set to Identified
              </Button>
              <Button 
                variant="outline" 
                className="w-full justify-start"
                onClick={() => handleStatusChange('MONITORING')}
                disabled={incident.status === 'MONITORING'}
              >
                Set to Monitoring
              </Button>
              <Button 
                variant="primary" 
                className="w-full justify-start"
                onClick={() => handleStatusChange('RESOLVED')}
                disabled={incident.status === 'RESOLVED'}
              >
                Mark as Resolved
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
