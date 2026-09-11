import { apiClient } from './client';

export interface AuditLogDto {
  id: string;
  organizationId: string;
  userId: string | null;
  userEmail: string | null;
  action: string;
  entityType: string;
  entityId: string;
  details: string;
  ipAddress: string | null;
  createdAt: string;
}

export interface PageDto<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export const AuditLogsApi = {
  getAuditLogs: async (orgId: string, page: number, size = 20): Promise<PageDto<AuditLogDto>> => {
    const { data } = await apiClient.get<PageDto<AuditLogDto>>(
      `/organizations/${orgId}/audit-logs`,
      { params: { page, size } }
    );
    return data;
  },
};
