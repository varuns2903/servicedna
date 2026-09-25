import { useQuery, useMutation } from '@tanstack/react-query';
import { BillingApi } from '@/api/billing.api';
import type { PlanType } from '@/api/billing.api';

export function useSubscription(orgId?: string) {
  return useQuery({
    queryKey: ['organizations', orgId, 'subscription'],
    queryFn: () => BillingApi.getSubscription(orgId!),
    enabled: !!orgId,
  });
}

export function usePlanUsage(orgId?: string) {
  return useQuery({
    queryKey: ['organizations', orgId, 'billing-usage'],
    queryFn: () => BillingApi.getUsage(orgId!),
    enabled: !!orgId,
  });
}

export function useCreatePortalSession() {
  return useMutation({
    mutationFn: (orgId: string) => BillingApi.createPortalSession(orgId),
  });
}

export function useCreateCheckoutSession() {
  return useMutation({
    mutationFn: ({ orgId, planType }: { orgId: string; planType: PlanType }) =>
      BillingApi.createCheckoutSession(orgId, planType),
  });
}
