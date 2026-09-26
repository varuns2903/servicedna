import { useEffect, useMemo, useState } from 'react';
import { ReactFlow, Background, Controls, MarkerType, useEdgesState, useNodesState, type Edge, type Node } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import dagre from 'dagre';
import { X } from 'lucide-react';
import { useEntryPoints, useFlow } from '@/hooks/useFlows';
import type { FlowCall, FlowOperation } from '@/api/flows.api';
import { OperationNode, type OperationNodeData } from './OperationNode';
import { CallTraces } from './CallTraces';

const nodeTypes = { operation: OperationNode };

const WINDOWS = [
  { minutes: 15, label: 'Last 15 min' },
  { minutes: 60, label: 'Last hour' },
  { minutes: 360, label: 'Last 6 hours' },
  { minutes: 1440, label: 'Last 24 hours' },
];

type Entry = { nodeId: string; operation: string };

function layout(nodes: Node[], edges: Edge[]) {
  const g = new dagre.graphlib.Graph();
  g.setDefaultEdgeLabel(() => ({}));
  g.setGraph({ rankdir: 'LR', ranksep: 110, nodesep: 30 });
  nodes.forEach((n) => g.setNode(n.id, { width: 230, height: 56 }));
  edges.forEach((e) => g.setEdge(e.source, e.target));
  dagre.layout(g);
  return nodes.map((n) => ({ ...n, position: { x: g.node(n.id).x - 115, y: g.node(n.id).y - 28 } }));
}

function label(call: FlowCall) {
  const parts: string[] = [call.protocol, `${call.callsPerMinute}/min`];
  if (call.p95Ms != null) parts.push(`p50 ${call.p50Ms}ms · p95 ${call.p95Ms}ms`);
  if (call.errorRate) parts.push(`${call.errorRate}% err`);
  return parts.join(' · ');
}

/**
 * The API flow graph: operations and the calls between them. Pick an entry point to see everything
 * it triggers, operation by operation; click a call for its numbers and recent traces.
 */
export function FlowsView() {
  const [windowMinutes, setWindowMinutes] = useState(60);
  const [entry, setEntry] = useState<Entry | null>(null);
  const [selected, setSelected] = useState<FlowCall | null>(null);
  const { data: entryPoints } = useEntryPoints(windowMinutes);
  const { data: flow, isLoading } = useFlow(windowMinutes, entry);
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<OperationNodeData>>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);

  // Start on the busiest entry point.
  useEffect(() => {
    if (!entry && entryPoints && entryPoints.length > 0) {
      setEntry({ nodeId: entryPoints[0].nodeId, operation: entryPoints[0].operation });
    }
  }, [entry, entryPoints]);

  const operationsById = useMemo(
    () => new Map((flow?.operations ?? []).map((o) => [o.id, o])),
    [flow],
  );

  useEffect(() => {
    if (!flow) return;
    const entryId = flow.entry ? `${flow.entry.nodeId}|${flow.entry.operation}` : null;
    const flowNodes: Node<OperationNodeData>[] = flow.operations.map((o: FlowOperation) => ({
      id: o.id,
      type: 'operation',
      position: { x: 0, y: 0 },
      data: { service: o.nodeName, operation: o.operation, kind: o.kind, isEntry: o.id === entryId },
    }));
    const flowEdges: Edge[] = flow.calls.map((c) => {
      const color = c.errorRate && c.errorRate >= 5 ? '#f43f5e' : c.errorRate ? '#f59e0b' : '#6b7280';
      return {
        id: `${c.source}->${c.target}|${c.protocol}`,
        source: c.source,
        target: c.target,
        type: 'smoothstep',
        label: label(c),
        labelStyle: { fill: '#9ca3af', fontSize: 10 },
        labelBgStyle: { fill: '#111317' },
        style: { stroke: color, strokeWidth: 1.5 + Math.min(2, c.callsPerMinute / 20) },
        markerEnd: { type: MarkerType.ArrowClosed, color },
        data: { call: c },
      };
    });
    setNodes(layout(flowNodes, flowEdges) as Node<OperationNodeData>[]);
    setEdges(flowEdges);
    setSelected(null);
  }, [flow, setNodes, setEdges]);

  const selectedSource = selected ? operationsById.get(selected.source) : undefined;
  const selectedTarget = selected ? operationsById.get(selected.target) : undefined;

  return (
    <div className="flex h-full flex-col">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-white">API Flows</h1>
          <p className="text-sm text-gray-400">Every call an operation triggers, across services — observed from traces.</p>
        </div>
        <select
          value={windowMinutes}
          onChange={(e) => setWindowMinutes(Number(e.target.value))}
          className="rounded-md border border-charcoal-700 bg-charcoal-900 p-2 text-sm text-white"
        >
          {WINDOWS.map((w) => (
            <option key={w.minutes} value={w.minutes}>
              {w.label}
            </option>
          ))}
        </select>
      </div>

      <div className="flex min-h-0 flex-1 gap-4">
        <aside className="w-64 shrink-0 overflow-y-auto rounded-lg border border-charcoal-700 bg-charcoal-900 p-2">
          <p className="px-2 pb-2 pt-1 text-xs uppercase tracking-wider text-gray-500">Entry points</p>
          {(entryPoints ?? []).map((e) => {
            const active = entry?.nodeId === e.nodeId && entry.operation === e.operation;
            return (
              <button
                key={`${e.nodeId}|${e.operation}`}
                type="button"
                onClick={() => setEntry({ nodeId: e.nodeId, operation: e.operation })}
                className={`mb-1 w-full rounded-md px-2 py-1.5 text-left ${active ? 'bg-emerald-500/10 text-white' : 'text-gray-300 hover:bg-charcoal-800'}`}
              >
                <div className="truncate font-mono text-xs">{e.operation}</div>
                <div className="text-[11px] text-gray-500">
                  {e.nodeName} · {e.calls} calls
                </div>
              </button>
            );
          })}
          {entryPoints && entryPoints.length === 0 && (
            <p className="px-2 text-sm text-gray-500">No traffic in this window yet.</p>
          )}
          <button
            type="button"
            onClick={() => setEntry(null)}
            className={`mt-2 w-full rounded-md border border-charcoal-700 px-2 py-1.5 text-left text-xs ${entry === null ? 'text-white' : 'text-gray-400 hover:text-gray-200'}`}
          >
            All operations (whole map)
          </button>
        </aside>

        <div className="relative min-w-0 flex-1 overflow-hidden rounded-lg border border-charcoal-700 bg-charcoal-900">
          {isLoading ? (
            <div className="flex h-full items-center justify-center text-gray-400">Loading flows…</div>
          ) : (
            <ReactFlow
              nodes={nodes}
              edges={edges}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onEdgeClick={(_, edge) => setSelected((edge.data as { call: FlowCall }).call)}
              onPaneClick={() => setSelected(null)}
              nodeTypes={nodeTypes}
              fitView
              colorMode="dark"
              className="bg-charcoal-900"
            >
              <Background color="#2a2e37" gap={16} />
              <Controls className="bg-charcoal-800 fill-gray-200" />
            </ReactFlow>
          )}

          {selected && selectedSource && selectedTarget && (
            <div className="absolute bottom-0 right-0 top-0 w-[420px] overflow-y-auto border-l border-charcoal-700 bg-charcoal-900/95 p-4 backdrop-blur">
              <div className="mb-3 flex items-start justify-between gap-2">
                <div className="min-w-0 text-sm">
                  <div className="truncate text-gray-400">
                    {selectedSource.nodeName} <span className="font-mono text-gray-200">{selectedSource.operation}</span>
                  </div>
                  <div className="truncate text-gray-400">
                    → {selectedTarget.nodeName} <span className="font-mono text-gray-200">{selectedTarget.operation}</span>
                  </div>
                </div>
                <button type="button" onClick={() => setSelected(null)} className="text-gray-500 hover:text-white">
                  <X className="h-4 w-4" />
                </button>
              </div>
              <dl className="mb-4 grid grid-cols-3 gap-2 text-center text-xs">
                <Stat label="calls/min" value={String(selected.callsPerMinute)} />
                <Stat label="p50" value={selected.p50Ms != null ? `${selected.p50Ms} ms` : '—'} />
                <Stat label="p95" value={selected.p95Ms != null ? `${selected.p95Ms} ms` : '—'} />
                <Stat label="calls" value={String(selected.calls)} />
                <Stat label="errors" value={String(selected.errors)} />
                <Stat label="protocol" value={selected.protocol} />
              </dl>
              <CallTraces source={selectedSource} target={selectedTarget} windowMinutes={windowMinutes} />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border border-charcoal-700 bg-charcoal-800 px-2 py-1.5">
      <div className="font-mono text-sm text-gray-100">{value}</div>
      <div className="text-gray-500">{label}</div>
    </div>
  );
}
