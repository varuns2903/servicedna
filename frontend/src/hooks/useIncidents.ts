import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { IncidentsApi } from '@/api/incidents.api';
import type { CreateIncidentRequest, UpdateIncidentStatusRequest, UpsertPostMortemRequest } from '@/api/incidents.api';

export function useIncidents(orgId?: string) {
  return useQuery({
    queryKey: ['organizations', orgId, 'incidents'],
    queryFn: () => IncidentsApi.getAll(orgId!),
    enabled: !!orgId,
  });
}

export function useIncident(orgId?: string, incidentId?: string) {
  return useQuery({
    queryKey: ['organizations', orgId, 'incidents', incidentId],
    queryFn: () => IncidentsApi.getById(orgId!, incidentId!),
    enabled: !!orgId && !!incidentId,
  });
}

export function useIncidentPostMortem(orgId?: string, incidentId?: string) {
  return useQuery({
    queryKey: ['organizations', orgId, 'incidents', incidentId, 'post-mortem'],
    queryFn: () => IncidentsApi.getPostMortem(orgId!, incidentId!),
    enabled: !!orgId && !!incidentId,
    retry: false // It returns 404 if not found, don't retry endlessly
  });
}

export function useCreateIncident() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, data }: { orgId: string; data: CreateIncidentRequest }) =>
      IncidentsApi.create(orgId, data),
    onSuccess: (_, { orgId }) => {
      queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'incidents'] });
    },
  });
}

export function useUpdateIncidentStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, incidentId, data }: { orgId: string; incidentId: string; data: UpdateIncidentStatusRequest }) =>
      IncidentsApi.updateStatus(orgId, incidentId, data),
    onSuccess: (data, { orgId, incidentId }) => {
      queryClient.setQueryData(['organizations', orgId, 'incidents', incidentId], data);
      queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'incidents'] });
    },
  });
}

export function useUpsertPostMortem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, incidentId, data }: { orgId: string; incidentId: string; data: UpsertPostMortemRequest }) =>
      IncidentsApi.upsertPostMortem(orgId, incidentId, data),
    onSuccess: (data, { orgId, incidentId }) => {
      queryClient.setQueryData(['organizations', orgId, 'incidents', incidentId, 'post-mortem'], data);
    },
  });
}
