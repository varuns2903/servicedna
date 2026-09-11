import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AuthApi } from '@/api/auth.api';
import { useAuthStore } from '@/stores/useAuthStore';

export function useSsoConfig() {
  return useQuery({
    queryKey: ['auth', 'sso-config'],
    queryFn: AuthApi.getSsoConfig,
    staleTime: 10 * 60 * 1000,
  });
}

export function useNotificationPreferences() {
  return useQuery({
    queryKey: ['auth', 'notification-preferences'],
    queryFn: AuthApi.getNotificationPreferences,
  });
}

export function useUpdateNotificationPreferences() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: AuthApi.updateNotificationPreferences,
    onSuccess: (data) => {
      queryClient.setQueryData(['auth', 'notification-preferences'], data);
    },
  });
}

export function useLogin() {
  const { setToken, setRefreshToken, setUser } = useAuthStore();
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      AuthApi.login(email, password),
    onSuccess: (data) => {
      setToken(data.token);
      setRefreshToken(data.refreshToken);
      setUser(data.user);
    },
  });
}

export function useRegister() {
  const { setToken, setRefreshToken, setUser } = useAuthStore();
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      AuthApi.register(email, password),
    onSuccess: (data) => {
      setToken(data.token);
      setRefreshToken(data.refreshToken);
      setUser(data.user);
    },
  });
}

export function useLogout() {
  const { refreshToken, logout } = useAuthStore();
  return useMutation({
    mutationFn: async () => {
      if (refreshToken) {
        // Best-effort: revoke server-side so the refresh token can't be replayed, but a logout
        // must still succeed locally even if the request fails (e.g. already offline).
        await AuthApi.logout(refreshToken).catch(() => undefined);
      }
    },
    onSettled: () => logout(),
  });
}

export function useVerifyEmail() {
  return useMutation({
    mutationFn: (token: string) => AuthApi.verifyEmail(token),
  });
}

export function useForgotPassword() {
  return useMutation({
    mutationFn: (email: string) => AuthApi.forgotPassword(email),
  });
}

export function useResendVerification() {
  return useMutation({
    mutationFn: (email: string) => AuthApi.resendVerification(email),
  });
}

export function useResetPassword() {
  return useMutation({
    mutationFn: ({ token, newPassword }: { token: string; newPassword: string }) =>
      AuthApi.resetPassword(token, newPassword),
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: ({ currentPassword, newPassword }: { currentPassword: string; newPassword: string }) =>
      AuthApi.changePassword(currentPassword, newPassword),
  });
}

export function useRequestEmailChange() {
  return useMutation({
    mutationFn: ({ password, newEmail }: { password: string; newEmail: string }) =>
      AuthApi.requestEmailChange(password, newEmail),
  });
}

export function useConfirmEmailChange() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (token: string) => AuthApi.confirmEmailChange(token),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['auth', 'me'] });
    },
  });
}
