import { useState } from 'react';
import { ChevronLeft, ChevronRight, FileText } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { useAuditLogs } from '@/hooks/useAuditLogs';

interface AuditLogTabProps {
  orgId: string;
}

export function AuditLogTab({ orgId }: AuditLogTabProps) {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useAuditLogs(orgId, page);

  if (isLoading && !data) {
    return <div className="text-gray-400">Loading audit log...</div>;
  }

  return (
    <div className="max-w-4xl space-y-4">
      <Card>
        <CardContent className="p-0">
          {!data || data.content.length === 0 ? (
            <div className="p-8 text-center text-gray-400">
              <FileText className="mx-auto mb-2 h-8 w-8 text-gray-600" />
              <p className="text-sm">No activity recorded yet.</p>
            </div>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-charcoal-700 text-left text-xs uppercase tracking-wider text-gray-500">
                  <th className="p-3">When</th>
                  <th className="p-3">Who</th>
                  <th className="p-3">Action</th>
                  <th className="p-3">Details</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((log) => (
                  <tr key={log.id} className="border-b border-charcoal-800 last:border-0">
                    <td className="whitespace-nowrap p-3 text-gray-400">
                      {new Date(log.createdAt).toLocaleString()}
                    </td>
                    <td className="whitespace-nowrap p-3 text-gray-300">
                      {log.userEmail || 'System'}
                    </td>
                    <td className="whitespace-nowrap p-3">
                      <span className="rounded bg-charcoal-700 px-2 py-0.5 text-xs font-medium text-gray-200">
                        {log.action.replace(/_/g, ' ')}
                      </span>
                    </td>
                    <td className="p-3 text-gray-400">{log.details}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </CardContent>
      </Card>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <span className="text-xs text-gray-500">
            Page {data.number + 1} of {data.totalPages} &middot; {data.totalElements} entries
          </span>
          <div className="flex space-x-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={data.first}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => p + 1)}
              disabled={data.last}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
