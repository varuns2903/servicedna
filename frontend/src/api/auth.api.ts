import { apiClient } from './client';

export interface UserDto {
  id: string;
  email: string;
  role: string;
}

export interface AuthResponse {
  token: string;
  user: UserDto;
}

export const AuthApi = {
  getMe: async (): Promise<UserDto> => {
    const { data } = await apiClient.get<UserDto>('/auth/me');
    return data;
  },

  login: async (email: string, password: string): Promise<AuthResponse> => {
    const { data } = await apiClient.post<AuthResponse>('/auth/login', { email, password });
    return data;
  },

  register: async (email: string, password: string): Promise<AuthResponse> => {
    const { data } = await apiClient.post<AuthResponse>('/auth/register', { email, password });
    return data;
  },

  verifyEmail: async (token: string): Promise<void> => {
    await apiClient.get('/auth/verify-email', { params: { token } });
  },

  forgotPassword: async (email: string): Promise<void> => {
    await apiClient.post('/auth/forgot-password', { email });
  },

  resendVerification: async (email: string): Promise<void> => {
    await apiClient.post('/auth/resend-verification', { email });
  },

  resetPassword: async (token: string, newPassword: string): Promise<void> => {
    await apiClient.post('/auth/reset-password', { token, newPassword });
  },
};
