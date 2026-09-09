import { useEffect, useState } from 'react';
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
import { useServiceMap } from '@/hooks/useServiceMap';
import { ServiceNode } from "./ServiceNode";
import type { ServiceNodeData } from './ServiceNode';

const nodeTypes = {
  serviceNode: ServiceNode,
};

const getLayoutedElements = (nodes: Node[], edges: Edge[], direction = 'TB') => {
  const dagreGraph = new dagre.graphlib.Graph();
  dagreGraph.setDefaultEdgeLabel(() => ({}));

  const nodeWidth = 200;
  const nodeHeight = 80;

  dagreGraph.setGraph({ rankdir: direction });

  nodes.forEach((node) => {
    dagreGraph.setNode(node.id, { width: nodeWidth, height: nodeHeight });
  });

  edges.forEach((edge) => {
    dagreGraph.setEdge(edge.source, edge.target);
  });

  dagre.layout(dagreGraph);

  nodes.forEach((node) => {
    const nodeWithPosition = dagreGraph.node(node.id);
    node.targetPosition = direction === 'TB' ? 'top' : 'left' as any;
    node.sourcePosition = direction === 'TB' ? 'bottom' : 'right' as any;

    node.position = {
      x: nodeWithPosition.x - nodeWidth / 2,
      y: nodeWithPosition.y - nodeHeight / 2,
    };
    return node;
  });

  return { nodes, edges };
};

export function DependencyGraph() {
  const { data: mapData, isLoading, isError } = useServiceMap();
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<ServiceNodeData>>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);

  useEffect(() => {
    if (mapData) {
      const initialNodes: Node<ServiceNodeData>[] = mapData.nodes.map((node) => ({
        id: node.id,
        type: 'serviceNode',
        position: { x: 0, y: 0 },
        data: {
          label: node.name,
          status: node.status,
          region: node.region,
          isDimmed: false,
        },
      }));

      const initialEdges: Edge[] = mapData.edges.map((edge) => ({
        id: `${edge.sourceId}-${edge.targetId}`,
        source: edge.sourceId,
        target: edge.targetId,
        type: 'smoothstep',
        animated: true,
        style: { stroke: '#4b5563', strokeWidth: 1.5 },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: '#4b5563',
        },
      }));

      const { nodes: layoutedNodes, edges: layoutedEdges } = getLayoutedElements(
        initialNodes,
        initialEdges,
        'TB'
      );

      setNodes(layoutedNodes as Node<ServiceNodeData>[]);
      setEdges(layoutedEdges);
    }
  }, [mapData, setNodes, setEdges]);

  // Blast Radius calculation
  useEffect(() => {
    if (!selectedNodeId || !mapData) {
      setNodes((nds) => nds.map((n) => ({ ...n, data: { ...n.data, isDimmed: false } })));
      setEdges((eds) =>
        eds.map((e) => ({
          ...e,
          style: { stroke: '#4b5563', strokeWidth: 1.5 },
          animated: true,
          markerEnd: { type: MarkerType.ArrowClosed, color: '#4b5563' },
        }))
      );
      return;
    }

    // Find all reachable nodes (up and down) using raw mapData to avoid effect loops
    const connectedNodeIds = new Set<string>();
    connectedNodeIds.add(selectedNodeId);

    let queue = [selectedNodeId];
    // Downstream (affected by this node failing)
    while (queue.length > 0) {
      const curr = queue.shift()!;
      const outgoing = mapData.edges.filter((e) => e.sourceId === curr).map((e) => e.targetId);
      for (const t of outgoing) {
        if (!connectedNodeIds.has(t)) {
          connectedNodeIds.add(t);
          queue.push(t);
        }
      }
    }

    queue = [selectedNodeId];
    // Upstream (services this node relies on)
    while (queue.length > 0) {
      const curr = queue.shift()!;
      const incoming = mapData.edges.filter((e) => e.targetId === curr).map((e) => e.sourceId);
      for (const src of incoming) {
        if (!connectedNodeIds.has(src)) {
          connectedNodeIds.add(src);
          queue.push(src);
        }
      }
    }

    setNodes((nds) =>
      nds.map((n) => ({
        ...n,
        data: { ...n.data, isDimmed: !connectedNodeIds.has(n.id) },
      }))
    );

    setEdges((eds) =>
      eds.map((e) => {
        const isConnected = connectedNodeIds.has(e.source) && connectedNodeIds.has(e.target);
        return {
          ...e,
          animated: isConnected,
          style: {
            stroke: isConnected ? '#10b981' : '#374151',
            strokeWidth: isConnected ? 2 : 1,
            opacity: isConnected ? 1 : 0.3,
          },
          markerEnd: {
            type: MarkerType.ArrowClosed,
            color: isConnected ? '#10b981' : '#374151',
          },
        };
      })
    );
  }, [selectedNodeId, mapData, setNodes, setEdges]);

  if (isError) {
    return (
      <div className="flex h-full flex-col">
        <h1 className="mb-6 text-2xl font-semibold tracking-tight text-white">Dependency Graph</h1>
        <div className="rounded-lg border border-rose-500/20 bg-rose-500/10 p-4 text-rose-500">
          Failed to load graph data.
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      <h1 className="mb-6 text-2xl font-semibold tracking-tight text-white">Dependency Graph</h1>
      
      <div className="flex-1 overflow-hidden rounded-lg border border-charcoal-700 bg-charcoal-900">
        {isLoading ? (
          <div className="flex h-full items-center justify-center">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-charcoal-700 border-t-emerald-500"></div>
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
