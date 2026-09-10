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

export function useLogin() {
  const { setToken, setUser } = useAuthStore();
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      AuthApi.login(email, password),
    onSuccess: (data) => {
      setToken(data.token);
      setUser(data.user);
    },
  });
}

export function useRegister() {
  const { setToken, setUser } = useAuthStore();
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      AuthApi.register(email, password),
    onSuccess: (data) => {
      setToken(data.token);
      setUser(data.user);
    },
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
