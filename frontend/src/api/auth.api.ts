import { apiClient } from './client';

export interface UserDto {
  id: string;
  email: string;
  role: string;
}

export const AuthApi = {
  getMe: async (): Promise<UserDto> => {
    const { data } = await apiClient.get<UserDto>('/auth/me');
    return data;
  },
};
