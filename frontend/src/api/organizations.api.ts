import { apiClient } from './client';

export interface OrganizationDto {
  id: string;
  name: string;
  createdAt: string;
}

export type OrganizationRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'READ_ONLY';

export interface OrganizationMemberDto {
  id: string;
  organizationId: string;
  email: string; // we mapped email instead of userId to make it readable in the UI
  role: OrganizationRole;
}

export interface InviteDto {
  id: string;
  email: string;
  role: OrganizationRole;
  token: string;
  expiresAt: string;
  status: 'PENDING' | 'ACCEPTED' | 'EXPIRED';
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

  getInvites: async (orgId: string): Promise<InviteDto[]> => {
    const { data } = await apiClient.get<InviteDto[]>(`/organizations/${orgId}/invites`);
    return data;
  },

  createInvite: async (orgId: string, email: string, role: OrganizationRole): Promise<InviteDto> => {
    const { data } = await apiClient.post<InviteDto>(`/organizations/${orgId}/invites`, { email, role });
    return data;
  }
};
