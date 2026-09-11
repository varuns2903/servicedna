import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { IncidentsApi } from '@/api/incidents.api';
import type {
  CreateIncidentRequest,
  IncidentDto,
  UpdateIncidentStatusRequest,
  UpsertPostMortemRequest,
} from '@/api/incidents.api';

function patchIncidentCaches(
  queryClient: ReturnType<typeof useQueryClient>,
  orgId: string,
  incidentId: string,
  patch: Partial<IncidentDto>
) {
  const listKey = ['organizations', orgId, 'incidents'];
  const detailKey = ['organizations', orgId, 'incidents', incidentId];

  const previousList = queryClient.getQueryData<IncidentDto[]>(listKey);
  const previousDetail = queryClient.getQueryData<IncidentDto>(detailKey);

  queryClient.setQueryData<IncidentDto[]>(listKey, (current) =>
    current?.map((incident) => (incident.id === incidentId ? { ...incident, ...patch } : incident))
  );
  queryClient.setQueryData<IncidentDto>(detailKey, (current) =>
    current ? { ...current, ...patch } : current
  );

  return { previousList, previousDetail, listKey, detailKey };
}

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
    onMutate: async ({ orgId, incidentId, data }) => {
      const listKey = ['organizations', orgId, 'incidents'];
      const detailKey = ['organizations', orgId, 'incidents', incidentId];
      await Promise.all([
        queryClient.cancelQueries({ queryKey: listKey }),
        queryClient.cancelQueries({ queryKey: detailKey }),
      ]);
      return patchIncidentCaches(queryClient, orgId, incidentId, { status: data.status });
    },
    onError: (_err, _vars, context) => {
      if (context?.previousList !== undefined) queryClient.setQueryData(context.listKey, context.previousList);
      if (context?.previousDetail !== undefined) queryClient.setQueryData(context.detailKey, context.previousDetail);
    },
    onSuccess: (data, { orgId, incidentId }) => {
      queryClient.setQueryData(['organizations', orgId, 'incidents', incidentId], data);
    },
    onSettled: (_data, _err, { orgId }) => {
      queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'incidents'] });
    },
  });
}

export function useAcknowledgeIncident() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, incidentId }: { orgId: string; incidentId: string }) =>
      IncidentsApi.acknowledge(orgId, incidentId),
    onMutate: async ({ orgId, incidentId }) => {
      const listKey = ['organizations', orgId, 'incidents'];
      const detailKey = ['organizations', orgId, 'incidents', incidentId];
      await Promise.all([
        queryClient.cancelQueries({ queryKey: listKey }),
        queryClient.cancelQueries({ queryKey: detailKey }),
      ]);
      return patchIncidentCaches(queryClient, orgId, incidentId, {
        acknowledgedAt: new Date().toISOString(),
      });
    },
    onError: (_err, _vars, context) => {
      if (context?.previousList !== undefined) queryClient.setQueryData(context.listKey, context.previousList);
      if (context?.previousDetail !== undefined) queryClient.setQueryData(context.detailKey, context.previousDetail);
    },
    onSuccess: (data, { orgId, incidentId }) => {
      queryClient.setQueryData(['organizations', orgId, 'incidents', incidentId], data);
    },
    onSettled: (_data, _err, { orgId }) => {
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
