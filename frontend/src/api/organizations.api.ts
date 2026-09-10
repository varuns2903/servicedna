import { apiClient } from './client';

export interface OrganizationDto {
  id: string;
  name: string;
  createdAt: string;
}

export type OrganizationRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER';

export interface OrganizationMemberDto {
  id: string;
  userId: string;
  email: string;
  role: OrganizationRole;
}

export interface InviteDto {
  id: string;
  organizationId: string;
  email: string;
  role: OrganizationRole;
  status: 'PENDING' | 'ACCEPTED' | 'EXPIRED';
  expiresAt: string;
  createdAt: string;
}

export const OrganizationsApi = {
  getOrganizations: async (): Promise<OrganizationDto[]> => {
    const { data } = await apiClient.get<OrganizationDto[]>('/organizations');
    return data;
  },

  createOrganization: async (name: string): Promise<OrganizationDto> => {
    const { data } = await apiClient.post<OrganizationDto>('/organizations', { name });
    return data;
  },

  updateOrganization: async (orgId: string, name: string): Promise<OrganizationDto> => {
    const { data } = await apiClient.put<OrganizationDto>(`/organizations/${orgId}`, { name });
    return data;
  },

  getMembers: async (orgId: string): Promise<OrganizationMemberDto[]> => {
    const { data } = await apiClient.get<OrganizationMemberDto[]>(`/organizations/${orgId}/members`);
    return data;
  },

  updateMemberRole: async (
    orgId: string,
    memberId: string,
    role: OrganizationRole
  ): Promise<OrganizationMemberDto> => {
    const { data } = await apiClient.patch<OrganizationMemberDto>(
      `/organizations/${orgId}/members/${memberId}`,
      { role }
    );
    return data;
  },

  removeMember: async (orgId: string, memberId: string): Promise<void> => {
    await apiClient.delete(`/organizations/${orgId}/members/${memberId}`);
  },

  getInvites: async (orgId: string): Promise<InviteDto[]> => {
    const { data } = await apiClient.get<InviteDto[]>(`/organizations/${orgId}/invites`);
    return data;
  },

  createInvite: async (orgId: string, email: string, role: OrganizationRole): Promise<InviteDto> => {
    const { data } = await apiClient.post<InviteDto>(`/organizations/${orgId}/invites`, { email, role });
    return data;
  }
};
