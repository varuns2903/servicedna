import { create } from 'zustand';

interface OrganizationState {
  selectedOrganizationId: string | null;
  setSelectedOrganizationId: (id: string | null) => void;
}

export const useOrganizationStore = create<OrganizationState>((set) => ({
  selectedOrganizationId: localStorage.getItem('sdna_org_id'),
  setSelectedOrganizationId: (id) => {
    if (id) {
      localStorage.setItem('sdna_org_id', id);
    } else {
      localStorage.removeItem('sdna_org_id');
    }
    set({ selectedOrganizationId: id });
  },
}));
