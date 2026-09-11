import axios from 'axios';
import { apiClient } from './client';

export interface UserDto {
  id: string;
  email: string;
  role: string;
}

export interface AuthResponse {
  token: string;
  refreshToken: string;
  user: UserDto;
}

export interface SsoConfigDto {
  oidcEnabled: boolean;
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

  changePassword: async (currentPassword: string, newPassword: string): Promise<void> => {
    await apiClient.post('/users/me/change-password', { currentPassword, newPassword });
  },

  requestEmailChange: async (password: string, newEmail: string): Promise<void> => {
    await apiClient.post('/users/me/change-email/request', { password, newEmail });
  },

  confirmEmailChange: async (token: string): Promise<void> => {
    await apiClient.get('/users/me/change-email/confirm', { params: { token } });
  },

  getSsoConfig: async (): Promise<SsoConfigDto> => {
    const { data } = await apiClient.get<SsoConfigDto>('/auth/sso-config');
    return data;
  },

  refresh: async (refreshToken: string): Promise<AuthResponse> => {
    // Deliberately bypasses apiClient's interceptors: this IS the recovery path they'd otherwise
    // trigger, and reusing apiClient here would risk a refresh call retrying itself.
    const { data } = await axios.post<AuthResponse>(
      `${apiClient.defaults.baseURL}/auth/refresh`,
      { refreshToken }
    );
    return data;
  },

  logout: async (refreshToken: string): Promise<void> => {
    await apiClient.post('/auth/logout', { refreshToken });
  },
};
