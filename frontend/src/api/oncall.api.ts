import { apiClient } from './client';

export interface OnCallMemberDto {
  organizationMemberId: string;
  userId: string;
  email: string;
  position: number;
}

export interface OnCallRotationDto {
  rotationLengthDays: number;
  startDate: string;
  members: OnCallMemberDto[];
  currentOnCall: OnCallMemberDto | null;
  currentShiftEndsOn: string | null;
}

export interface UpsertOnCallRotationRequest {
  rotationLengthDays: number;
  startDate: string;
  organizationMemberIds: string[];
}

export const OnCallApi = {
  getRotation: async (orgId: string): Promise<OnCallRotationDto> => {
    const { data } = await apiClient.get<OnCallRotationDto>(`/organizations/${orgId}/on-call`);
    return data;
  },

  upsertRotation: async (
    orgId: string,
    request: UpsertOnCallRotationRequest
  ): Promise<OnCallRotationDto> => {
    const { data } = await apiClient.put<OnCallRotationDto>(
      `/organizations/${orgId}/on-call`,
      request
    );
    return data;
  },
};
