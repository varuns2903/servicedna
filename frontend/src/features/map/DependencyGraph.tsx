import { useEffect, useMemo, useState } from 'react';
import {
  ReactFlow,
  Controls,
  Background,
  useNodesState,
  useEdgesState,
  type Node,
  type Edge,
  MarkerType,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import dagre from 'dagre';
import { useGraph } from '@/hooks/useGraph';
import type { GraphEdge } from '@/api/graph.api';
import { ServiceNode } from './ServiceNode';
import type { ServiceNodeData } from './ServiceNode';

const nodeTypes = {
  serviceNode: ServiceNode,
};

const WINDOWS = [
  { minutes: 15, label: 'Last 15 min' },
  { minutes: 60, label: 'Last hour' },
  { minutes: 360, label: 'Last 6 hours' },
  { minutes: 1440, label: 'Last 24 hours' },
  { minutes: 10080, label: 'Last 7 days' },
];

type View = 'all' | 'observed' | 'declared';
type Drift = 'undeclared' | 'unseen' | null;

/** Colour and dash pattern encode drift: solid = declared and seen, orange = seen but undeclared, grey dashed = declared but unseen. */
function edgeStyle(edge: GraphEdge, highlighted: boolean, dimmed: boolean) {
  const color = highlighted ? '#10b981' : edge.observed && !edge.declared ? '#f59e0b' : edge.observed ? '#6b7280' : '#4b5563';
  return {
    style: {
      stroke: color,
      strokeWidth: highlighted ? 2.5 : edge.observed ? 1.5 + Math.min(2, edge.callsPerMinute / 20) : 1.25,
      strokeDasharray: edge.observed ? (edge.declared ? undefined : '6 3') : '3 4',
      opacity: dimmed ? 0.2 : 1,
    },
    markerEnd: { type: MarkerType.ArrowClosed, color },
    // Not animated: React Flow animates edges as moving dashes, which would read as "undeclared".
    animated: false,
  };
}

function edgeLabel(edge: GraphEdge): string | undefined {
  if (!edge.observed) return 'declared, not seen';
  const parts = [`${edge.callsPerMinute}/min`];
  if (edge.p95Ms != null) parts.push(`p95 ${edge.p95Ms}ms`);
  if (edge.errorRate) parts.push(`${edge.errorRate}% err`);
  return parts.join(' · ');
}

const layout = (nodes: Node[], edges: Edge[]) => {
  const g = new dagre.graphlib.Graph();
  g.setDefaultEdgeLabel(() => ({}));
  g.setGraph({ rankdir: 'TB', ranksep: 90, nodesep: 50 });
  nodes.forEach((node) => g.setNode(node.id, { width: 200, height: 80 }));
  edges.forEach((edge) => g.setEdge(edge.source, edge.target));
  dagre.layout(g);
  return nodes.map((node) => {
    const p = g.node(node.id);
    return { ...node, position: { x: p.x - 100, y: p.y - 40 } };
  });
};

export function DependencyGraph() {
  const [windowMinutes, setWindowMinutes] = useState(60);
  const [view, setView] = useState<View>('all');
  const [drift, setDrift] = useState<Drift>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const { data: graph, isLoading, isError } = useGraph(windowMinutes);
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<ServiceNodeData>>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);

  const visibleEdges = useMemo(
    () =>
      (graph?.edges ?? []).filter((e) =>
        view === 'observed' ? e.observed : view === 'declared' ? e.declared : true,
      ),
    [graph, view],
  );
  const undeclared = (graph?.edges ?? []).filter((e) => e.observed && !e.declared).length;
  const unseen = (graph?.edges ?? []).filter((e) => e.declared && !e.observed).length;

  // Everything reachable from the selected node, upstream and downstream: its blast radius.
  const connected = useMemo(() => {
    if (!selectedNodeId) return null;
    const ids = new Set([selectedNodeId]);
    for (const [from, to] of [['source', 'target'], ['target', 'source']] as const) {
      const queue = [selectedNodeId];
      while (queue.length) {
        const current = queue.shift()!;
        for (const e of visibleEdges) {
          if (e[from] === current && !ids.has(e[to])) {
            ids.add(e[to]);
            queue.push(e[to]);
          }
        }
      }
    }
    return ids;
  }, [selectedNodeId, visibleEdges]);

  useEffect(() => {
    if (!graph) return;
    const used = new Set(visibleEdges.flatMap((e) => [e.source, e.target]));
    const graphNodes: Node<ServiceNodeData>[] = graph.nodes
      .filter((n) => n.kind === 'SERVICE' || used.has(n.id))
      .map((n) => ({
        id: n.id,
        type: 'serviceNode',
        position: { x: 0, y: 0 },
        data: {
          label: n.name,
          kind: n.kind,
          status: n.status,
          detail: n.kind === 'SERVICE' ? [n.environment, n.language].filter(Boolean).join(' · ') || null : null,
          isDimmed: connected ? !connected.has(n.id) : false,
        },
      }));
    const graphEdges: Edge[] = visibleEdges.map((e) => {
      const inDrift = drift === 'undeclared' ? e.observed && !e.declared : drift === 'unseen' ? e.declared && !e.observed : false;
      const onPath = connected ? connected.has(e.source) && connected.has(e.target) : false;
      const dimmed = (connected !== null && !onPath) || (drift !== null && !inDrift);
      return {
        id: `${e.source}->${e.target}`,
        source: e.source,
        target: e.target,
        type: 'smoothstep',
        label: edgeLabel(e),
        labelStyle: { fill: '#9ca3af', fontSize: 10 },
        labelBgStyle: { fill: '#111317' },
        ...edgeStyle(e, onPath || inDrift, dimmed),
      };
    });
    setNodes(layout(graphNodes, graphEdges) as Node<ServiceNodeData>[]);
    setEdges(graphEdges);
  }, [graph, visibleEdges, connected, drift, setNodes, setEdges]);

  return (
    <div className="flex h-full flex-col">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-white">Dependency Graph</h1>
        <div className="flex flex-wrap items-center gap-2">
          <select
            value={view}
            onChange={(e) => setView(e.target.value as View)}
            className="rounded-md border border-charcoal-700 bg-charcoal-900 p-2 text-sm text-white"
          >
            <option value="all">Declared + observed</option>
            <option value="observed">Observed from traffic</option>
            <option value="declared">Declared only</option>
          </select>
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
      </div>

      {graph && (
        <div className="mb-3 flex flex-wrap items-center gap-2 text-xs">
          <DriftChip
            active={drift === 'undeclared'}
            onClick={() => setDrift(drift === 'undeclared' ? null : 'undeclared')}
            count={undeclared}
            className="border-amber-500/40 text-amber-300"
            label="seen in traffic but not declared"
          />
          <DriftChip
            active={drift === 'unseen'}
            onClick={() => setDrift(drift === 'unseen' ? null : 'unseen')}
            count={unseen}
            className="border-gray-500/40 text-gray-300"
            label="declared but not seen"
          />
          <span className="ml-auto text-gray-500">
            Solid: declared and seen · <span className="text-amber-400">orange dashed</span>: undeclared ·{' '}
            grey dotted: not seen · click a node for its blast radius
          </span>
        </div>
      )}

      <div className="flex-1 overflow-hidden rounded-lg border border-charcoal-700 bg-charcoal-900">
        {isError ? (
          <div className="m-4 rounded-lg border border-rose-500/20 bg-rose-500/10 p-4 text-rose-500">
            Failed to load graph data.
          </div>
        ) : isLoading ? (
          <div className="flex h-full items-center justify-center">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-charcoal-700 border-t-emerald-500" />
          </div>
        ) : (
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onNodeClick={(_, node) => setSelectedNodeId(node.id)}
            onPaneClick={() => setSelectedNodeId(null)}
            nodeTypes={nodeTypes}
            fitView
            className="bg-charcoal-900"
            colorMode="dark"
          >
            <Background color="#2a2e37" gap={16} />
            <Controls className="bg-charcoal-800 fill-gray-200" />
          </ReactFlow>
        )}
      </div>
    </div>
  );
}

function DriftChip({
  active,
  onClick,
  count,
  className,
  label,
}: {
  active: boolean;
  onClick: () => void;
  count: number;
  className: string;
  label: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={count === 0}
      className={`rounded-full border px-3 py-1 ${className} ${active ? 'bg-white/10' : ''} disabled:opacity-40`}
    >
      {count} {count === 1 ? 'edge' : 'edges'} {label}
    </button>
  );
}
