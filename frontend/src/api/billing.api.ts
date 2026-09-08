import { apiClient } from './client';

export type PlanType = 'FREE' | 'PRO' | 'ENTERPRISE';
export type SubscriptionStatus = 'ACTIVE' | 'CANCELED' | 'PAST_DUE' | 'INCOMPLETE';

export interface SubscriptionDto {
  planType: PlanType;
  status: SubscriptionStatus;
  currentPeriodEnd: string;
}

export interface CheckoutSessionResponse {
  url: string;
}

export const BillingApi = {
  getSubscription: async (orgId: string) => {
    const res = await apiClient.get<SubscriptionDto>(`/organizations/${orgId}/billing/subscription`);
    return res.data;
  },

  createCheckoutSession: async (orgId: string, planType: PlanType) => {
    const res = await apiClient.post<CheckoutSessionResponse>(`/organizations/${orgId}/billing/checkout-session`, { planType });
    return res.data;
  }
};
