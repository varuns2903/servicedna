import { create } from 'zustand';

interface User {
  id: string;
  email: string;
  role: string;
}

interface AuthState {
  token: string | null;
  refreshToken: string | null;
  user: User | null;
  setToken: (token: string) => void;
  setRefreshToken: (refreshToken: string) => void;
  setUser: (user: User) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: localStorage.getItem('sdna_token'),
  refreshToken: localStorage.getItem('sdna_refresh_token'),
  user: null,
  setToken: (token) => {
    localStorage.setItem('sdna_token', token);
    set({ token });
  },
  setRefreshToken: (refreshToken) => {
    localStorage.setItem('sdna_refresh_token', refreshToken);
    set({ refreshToken });
  },
  setUser: (user) => set({ user }),
  logout: () => {
    localStorage.removeItem('sdna_token');
    localStorage.removeItem('sdna_refresh_token');
    set({ token: null, refreshToken: null, user: null });
  },
}));
