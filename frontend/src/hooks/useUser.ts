import { useQuery } from '@tanstack/react-query';
import { AuthApi } from '@/api/auth.api';
import { useAuthStore } from '@/stores/useAuthStore';

export function useUser() {
  const token = useAuthStore((state) => state.token);
  
  return useQuery({
    queryKey: ['auth', 'me'],
    queryFn: AuthApi.getMe,
    enabled: !!token,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}
