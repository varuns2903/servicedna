import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import { StatusIndicator } from "@/components/status/StatusIndicator";
import type { ServiceStatus } from '@/components/status/StatusIndicator';
import { cn } from '@/utils/cn';

export interface ServiceNodeData extends Record<string, unknown> {
  label: string;
  status: ServiceStatus;
  latencyMs: number | null;
  environment: string;
  isDimmed?: boolean;
}

export const ServiceNode = memo(({ data, selected }: { data: ServiceNodeData; selected: boolean }) => {
  const statusColors = {
    HEALTHY: 'border-emerald-500/50 shadow-emerald-500/10',
    DEGRADED: 'border-amber-400/50 shadow-amber-400/10',
    DOWN: 'border-rose-500/50 shadow-rose-500/10',
    RECOVERING: 'border-blue-400/50 shadow-blue-400/10',
    UNKNOWN: 'border-gray-500/50 shadow-gray-500/10',
  };

  return (
    <>
      <Handle type="target" position={Position.Top} className="!w-2 !h-2 !bg-gray-400 !border-charcoal-900" />
      
      <div 
        className={cn(
          'min-w-[180px] rounded-lg border bg-charcoal-800 p-3 shadow-lg transition-all',
          statusColors[data.status] || statusColors.UNKNOWN,
          selected ? 'ring-2 ring-emerald-500 ring-offset-2 ring-offset-charcoal-900 scale-105' : '',
          data.isDimmed ? 'opacity-30 grayscale' : 'opacity-100'
        )}
      >
        <div className="flex flex-col space-y-2">
          <div className="flex items-start justify-between">
            <span className="font-semibold text-gray-100 truncate pr-2" title={data.label}>{data.label}</span>
          </div>
          
          <div className="flex items-center justify-between">
            <StatusIndicator status={data.status} size="sm" showLabel={false} />
            <span className="text-xs font-mono text-gray-400">
              {data.latencyMs ? `${data.latencyMs}ms` : '—'}
            </span>
          </div>
        </div>
      </div>

      <Handle type="source" position={Position.Bottom} className="!w-2 !h-2 !bg-gray-400 !border-charcoal-900" />
    </>
  );
});
