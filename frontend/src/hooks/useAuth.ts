import { useMutation } from '@tanstack/react-query';
import { AuthApi } from '@/api/auth.api';
import { useAuthStore } from '@/stores/useAuthStore';

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

export function useResetPassword() {
  return useMutation({
    mutationFn: ({ token, newPassword }: { token: string; newPassword: string }) =>
      AuthApi.resetPassword(token, newPassword),
  });
}
