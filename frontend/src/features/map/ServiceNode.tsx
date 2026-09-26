import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import { Database, Globe, MessagesSquare } from 'lucide-react';
import { StatusIndicator } from '@/components/status/StatusIndicator';
import type { ServiceStatus } from '@/components/status/StatusIndicator';
import type { NodeKind } from '@/api/graph.api';
import { cn } from '@/utils/cn';

export interface ServiceNodeData extends Record<string, unknown> {
  label: string;
  kind: NodeKind;
  status: ServiceStatus | null;
  /** Environment for services; the kind of dependency otherwise. */
  detail: string | null;
  isDimmed?: boolean;
}

const KIND_ICON = { DATABASE: Database, EXTERNAL: Globe, TOPIC: MessagesSquare } as const;
const KIND_LABEL = { DATABASE: 'database', EXTERNAL: 'external', TOPIC: 'topic' } as const;

export const ServiceNode = memo(({ data, selected }: { data: ServiceNodeData; selected: boolean }) => {
  const statusColors = {
    HEALTHY: 'border-emerald-500/50 shadow-emerald-500/10',
    DEGRADED: 'border-amber-400/50 shadow-amber-400/10',
    DOWN: 'border-rose-500/50 shadow-rose-500/10',
    RECOVERING: 'border-blue-400/50 shadow-blue-400/10',
    UNKNOWN: 'border-gray-500/50 shadow-gray-500/10',
  };
  const isService = data.kind === 'SERVICE';
  const Icon = data.kind === 'SERVICE' ? null : KIND_ICON[data.kind];

  return (
    <>
      <Handle type="target" position={Position.Top} className="!w-2 !h-2 !bg-gray-400 !border-charcoal-900" />

      <div
        className={cn(
          'min-w-[180px] rounded-lg border p-3 shadow-lg transition-all',
          isService
            ? ['bg-charcoal-800', statusColors[data.status ?? 'UNKNOWN'] || statusColors.UNKNOWN]
            : 'border-dashed border-sky-500/40 bg-charcoal-900',
          selected ? 'ring-2 ring-emerald-500 ring-offset-2 ring-offset-charcoal-900 scale-105' : '',
          data.isDimmed ? 'opacity-30 grayscale' : 'opacity-100'
        )}
      >
        <div className="flex flex-col space-y-2">
          <div className="flex items-center gap-2">
            {Icon && <Icon className="h-4 w-4 shrink-0 text-sky-400" />}
            <span className="truncate font-semibold text-gray-100" title={data.label}>
              {data.label}
            </span>
          </div>
          <div className="flex items-center justify-between">
            {isService ? (
              <StatusIndicator status={data.status ?? 'UNKNOWN'} size="sm" showLabel={false} />
            ) : (
              <span className="text-xs text-sky-400/80">{data.kind === 'SERVICE' ? '' : KIND_LABEL[data.kind]}</span>
            )}
            <span className="font-mono text-xs text-gray-400">{data.detail || '—'}</span>
          </div>
        </div>
      </div>

      <Handle type="source" position={Position.Bottom} className="!w-2 !h-2 !bg-gray-400 !border-charcoal-900" />
    </>
  );
});
