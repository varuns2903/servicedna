import React, { createContext, useContext, useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuthStore } from '@/stores/useAuthStore';
import { useOrganizationStore } from '@/stores/useOrganizationStore';
import { useQueryClient } from '@tanstack/react-query';

interface WebSocketContextType {
  connected: boolean;
}

const WebSocketContext = createContext<WebSocketContextType>({ connected: false });

export const useWebSocket = () => useContext(WebSocketContext);

export function WebSocketProvider({ children }: { children: React.ReactNode }) {
  const [connected, setConnected] = useState(false);
  const token = useAuthStore((state) => state.token);
  const currentOrgId = useOrganizationStore((state) => state.selectedOrganizationId);
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!token || !currentOrgId) {
      setConnected(false);
      return;
    }

    // Determine WebSocket URL based on API URL
    const baseUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';
    // We expect the WS endpoint to be at the root context /ws, not under /api/v1
    const wsUrl = baseUrl.replace('/api/v1', '/ws');

    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      debug: (str) => {
        if (import.meta.env.DEV) {
          console.log('[STOMP]', str);
        }
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    client.onConnect = () => {
      setConnected(true);
      
      // Subscribe to organization dashboard updates
      const topic = `/topic/organizations/${currentOrgId}/dashboard`;
      client.subscribe(topic, (message) => {
        if (message.body) {
          const payload = JSON.parse(message.body);
          // 1. Update the dashboard cache instantly
          queryClient.setQueryData(['organizations', currentOrgId, 'dashboard'], payload);
          
          // 2. Invalidate other caches (services, map, incidents) so they refetch silently in the background
          queryClient.invalidateQueries({ queryKey: ['organizations', currentOrgId, 'services'] });
          queryClient.invalidateQueries({ queryKey: ['organizations', currentOrgId, 'incidents'] });
          queryClient.invalidateQueries({ queryKey: ['organizations', currentOrgId, 'map'] });
        }
      });
    };

    client.onStompError = (frame) => {
      console.error('Broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
    };

    client.onWebSocketClose = () => {
      setConnected(false);
    };

    client.activate();

    return () => {
      client.deactivate();
    };
  }, [token, currentOrgId, queryClient]);

  return (
    <WebSocketContext.Provider value={{ connected }}>
      {children}
    </WebSocketContext.Provider>
  );
}
