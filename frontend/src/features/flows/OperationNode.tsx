import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import { Database, Globe, MessagesSquare, Server } from 'lucide-react';
import type { NodeKind } from '@/api/graph.api';
import { cn } from '@/utils/cn';

export interface OperationNodeData extends Record<string, unknown> {
  service: string;
  operation: string;
  kind: NodeKind;
  isEntry: boolean;
}

const ICONS = { SERVICE: Server, DATABASE: Database, EXTERNAL: Globe, TOPIC: MessagesSquare } as const;

export const OperationNode = memo(({ data }: { data: OperationNodeData }) => {
  const Icon = ICONS[data.kind];
  return (
    <>
      <Handle type="target" position={Position.Left} className="!h-2 !w-2 !border-charcoal-900 !bg-gray-400" />
      <div
        className={cn(
          'w-[230px] rounded-md border px-3 py-2 shadow',
          data.isEntry ? 'border-emerald-500/60 bg-emerald-500/5' : data.kind === 'SERVICE' ? 'border-charcoal-600 bg-charcoal-800' : 'border-dashed border-sky-500/40 bg-charcoal-900',
        )}
      >
        <div className="flex items-center gap-1.5 text-[11px] text-gray-400">
          <Icon className="h-3 w-3 shrink-0" />
          <span className="truncate">{data.service}</span>
        </div>
        <div className="mt-0.5 truncate font-mono text-xs text-gray-100" title={data.operation}>
          {data.operation || '(unknown operation)'}
        </div>
      </div>
      <Handle type="source" position={Position.Right} className="!h-2 !w-2 !border-charcoal-900 !bg-gray-400" />
    </>
  );
});
