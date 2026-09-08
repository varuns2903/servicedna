import { useQuery } from '@tanstack/react-query';
import { OrganizationsApi } from '@/api/organizations.api';
import { useAuthStore } from '@/stores/useAuthStore';
import { useOrganizationStore } from '@/stores/useOrganizationStore';
import { useEffect } from 'react';

export function useOrganizations() {
  const token = useAuthStore((state) => state.token);
  const { selectedOrganizationId, setSelectedOrganizationId } = useOrganizationStore();
  
  const query = useQuery({
    queryKey: ['organizations'],
    queryFn: OrganizationsApi.getOrganizations,
    enabled: !!token,
    staleTime: 10 * 60 * 1000,
  });

  // Auto-select the first organization if none is selected
  useEffect(() => {
    if (query.isSuccess && query.data && query.data.length > 0) {
      if (!selectedOrganizationId || !query.data.find(o => o.id === selectedOrganizationId)) {
        setSelectedOrganizationId(query.data[0].id);
      }
    }
  }, [query.isSuccess, query.data, selectedOrganizationId, setSelectedOrganizationId]);

  return query;
}
