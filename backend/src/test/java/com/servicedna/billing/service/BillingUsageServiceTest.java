package com.servicedna.billing.service;

import com.servicedna.billing.domain.PlanType;
import com.servicedna.billing.domain.Subscription;
import com.servicedna.billing.domain.SubscriptionStatus;
import com.servicedna.billing.repository.SubscriptionRepository;
import com.servicedna.organization.domain.Organization;
import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingUsageServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ServiceRepository serviceRepository;

    private BillingUsageService billingUsageService;

    @BeforeEach
    void setUp() {
        billingUsageService = new BillingUsageService(subscriptionRepository, serviceRepository, "sk_test_dummy");
        billingUsageService.init();
    }

    @Test
    void shouldSkipUsageReportingForFreeOrMockSubscriptions() {
        Organization org = new Organization(UUID.randomUUID(), "Org");
        
        Subscription subFree = new Subscription(UUID.randomUUID(), org);
        subFree.setPlanType(PlanType.FREE);
        
        Subscription subMock = new Subscription(UUID.randomUUID(), org);
        subMock.setPlanType(PlanType.PRO);
        subMock.setStripeSubscriptionId("sub_test_mock_123");

        when(subscriptionRepository.findAll()).thenReturn(List.of(subFree, subMock));
        when(serviceRepository.findByOrganizationId(org.getId())).thenReturn(List.of(new Service(), new Service()));

        billingUsageService.reportUsageToStripe();

        verify(serviceRepository, times(1)).findByOrganizationId(org.getId());
    }
}
