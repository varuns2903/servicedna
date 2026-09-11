import { formatDistanceToNow } from 'date-fns';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { useIncidentEvents } from '@/hooks/useIncidents';
import { FilePlus, RefreshCw, CheckCheck, ArrowUpCircle, FileText } from 'lucide-react';
import type { IncidentEventType } from '@/api/incidents.api';

interface IncidentTimelineProps {
  orgId: string;
  incidentId: string;
}

const EVENT_ICON: Record<IncidentEventType, typeof FilePlus> = {
  CREATED: FilePlus,
  STATUS_CHANGED: RefreshCw,
  ACKNOWLEDGED: CheckCheck,
  ESCALATED: ArrowUpCircle,
  POST_MORTEM_UPDATED: FileText,
};

const EVENT_COLOR: Record<IncidentEventType, string> = {
  CREATED: 'text-gray-400 bg-charcoal-700',
  STATUS_CHANGED: 'text-blue-400 bg-blue-500/10',
  ACKNOWLEDGED: 'text-emerald-400 bg-emerald-500/10',
  ESCALATED: 'text-amber-400 bg-amber-500/10',
  POST_MORTEM_UPDATED: 'text-fuchsia-400 bg-fuchsia-500/10',
};

export function IncidentTimeline({ orgId, incidentId }: IncidentTimelineProps) {
  const { data: events, isLoading } = useIncidentEvents(orgId, incidentId);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Timeline</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <p className="text-sm text-gray-400">Loading timeline...</p>
        ) : !events || events.length === 0 ? (
          <p className="text-sm text-gray-500">No events yet.</p>
        ) : (
          <ul className="space-y-4">
            {events.map((event, i) => {
              const Icon = EVENT_ICON[event.eventType];
              return (
                <li key={event.id} className="flex gap-3">
                  <div className="flex flex-col items-center">
                    <span
                      className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${EVENT_COLOR[event.eventType]}`}
                    >
                      <Icon className="h-3.5 w-3.5" />
                    </span>
                    {i < events.length - 1 && <span className="mt-1 w-px flex-1 bg-charcoal-700" />}
                  </div>
                  <div className="pb-4">
                    <p className="text-sm text-gray-200">{event.message}</p>
                    <p className="text-xs text-gray-500">
                      {formatDistanceToNow(new Date(event.createdAt), { addSuffix: true })}
                      {event.actorEmail && <> &middot; {event.actorEmail}</>}
                    </p>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
