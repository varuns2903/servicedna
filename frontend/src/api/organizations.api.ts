import { apiClient } from './client';

export interface OrganizationDto {
  id: string;
  name: string;
  ownerId: string;
  createdAt: string;
}

export const OrganizationsApi = {
  getOrganizations: async (): Promise<OrganizationDto[]> => {
    const { data } = await apiClient.get<OrganizationDto[]>('/organizations');
    return data;
  },
};
