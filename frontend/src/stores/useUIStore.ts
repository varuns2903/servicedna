import { create } from 'zustand';

interface UIState {
  sidebarCollapsed: boolean;
  toggleSidebar: () => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
}

export const useUIStore = create<UIState>((set) => ({
  sidebarCollapsed: localStorage.getItem('sdna_sidebar') === 'true',
  toggleSidebar: () => set((state) => {
    const next = !state.sidebarCollapsed;
    localStorage.setItem('sdna_sidebar', String(next));
    return { sidebarCollapsed: next };
  }),
  setSidebarCollapsed: (collapsed) => {
    localStorage.setItem('sdna_sidebar', String(collapsed));
    set({ sidebarCollapsed: collapsed });
  },
}));
