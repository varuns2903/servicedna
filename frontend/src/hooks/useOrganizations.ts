import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { OrganizationsApi } from '@/api/organizations.api';
import type { OrganizationRole } from '@/api/organizations.api';
import { useAuthStore } from '@/stores/useAuthStore';
import { useOrganizationStore } from '@/stores/useOrganizationStore';
import { useEffect } from 'react';

export function useOrganizations() {
  const token = useAuthStore((state) => state.token);
  const { selectedOrganizationId, setSelectedOrganizationId } = useOrganizationStore();
  
  const query = useQuery({
    queryKey: ['organizations'],
    queryFn: OrganizationsApi.getOrganizations,
    enabled: !!token,
    staleTime: 10 * 60 * 1000,
  });

  // Auto-select the first organization if none is selected
  useEffect(() => {
    if (query.isSuccess && query.data && query.data.length > 0) {
      if (!selectedOrganizationId || !query.data.find(o => o.id === selectedOrganizationId)) {
        setSelectedOrganizationId(query.data[0].id);
      }
    }
  }, [query.isSuccess, query.data, selectedOrganizationId, setSelectedOrganizationId]);

  return query;
}

export function useCreateOrganization() {
  const queryClient = useQueryClient();
  const { setSelectedOrganizationId } = useOrganizationStore();
  return useMutation({
    mutationFn: (name: string) => OrganizationsApi.createOrganization(name),
    onSuccess: (org) => {
      queryClient.invalidateQueries({ queryKey: ['organizations'] });
      setSelectedOrganizationId(org.id);
    }
  });
}

export function useUpdateOrganization() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, name }: { orgId: string; name: string }) => 
      OrganizationsApi.updateOrganization(orgId, name),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations'] });
    }
  });
}

export function useOrganizationMembers(orgId?: string) {
  return useQuery({
    queryKey: ['organizations', orgId, 'members'],
    queryFn: () => OrganizationsApi.getMembers(orgId!),
    enabled: !!orgId,
  });
}

export function useUpdateMemberRole() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, memberId, role }: { orgId: string; memberId: string; role: OrganizationRole }) =>
      OrganizationsApi.updateMemberRole(orgId, memberId, role),
    onSuccess: (_, { orgId }) => {
      queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'members'] });
    }
  });
}

export function useRemoveMember() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, memberId }: { orgId: string; memberId: string }) =>
      OrganizationsApi.removeMember(orgId, memberId),
    onSuccess: (_, { orgId }) => {
      queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'members'] });
    }
  });
}

export function useOrganizationInvites(orgId?: string) {
  return useQuery({
    queryKey: ['organizations', orgId, 'invites'],
    queryFn: () => OrganizationsApi.getInvites(orgId!),
    enabled: !!orgId,
  });
}

export function useCreateInvite() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, email, role }: { orgId: string; email: string; role: OrganizationRole }) =>
      OrganizationsApi.createInvite(orgId, email, role),
    onSuccess: (_, { orgId }) => {
      queryClient.invalidateQueries({ queryKey: ['organizations', orgId, 'invites'] });
    }
  });
}
