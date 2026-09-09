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
