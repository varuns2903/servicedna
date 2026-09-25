package com.servicedna.telemetry.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.service.domain.Service;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.telemetry.domain.ServicePing;
import com.servicedna.telemetry.repository.ServicePingRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.servicedna.billing.domain.PlanType;
import com.servicedna.billing.domain.Subscription;
import com.servicedna.billing.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class PingRetentionServiceTest {

  @Autowired private PingRetentionService pingRetentionService;
  @Autowired private ServicePingRepository servicePingRepository;
  @Autowired private ServiceRepository serviceRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private SubscriptionRepository subscriptionRepository;

  @Test
  @Transactional
  void shouldDeleteOnlyPingsOlderThanRetentionWindow() {
    Organization org = organizationRepository.save(new Organization(UUID.randomUUID(), "Retention Org"));
    Service service =
        serviceRepository.save(
            new Service(UUID.randomUUID(), org, "retention-svc", null, null, "global", "key-1"));

    ServicePing oldPing =
        servicePingRepository.saveAndFlush(
            new ServicePing(UUID.randomUUID(), service, ServiceStatus.HEALTHY, 10, null));
    entityManager
        .createNativeQuery("update service_pings set created_at = :cutoff where id = :id")
        .setParameter("cutoff", OffsetDateTime.now().minusDays(200))
        .setParameter("id", oldPing.getId())
        .executeUpdate();

    ServicePing recentPing =
        servicePingRepository.saveAndFlush(
            new ServicePing(UUID.randomUUID(), service, ServiceStatus.HEALTHY, 12, null));

    pingRetentionService.cleanupOldPings();

    List<ServicePing> remaining =
        servicePingRepository.findByServiceIdOrderByCreatedAtDesc(service.getId());
    assertThat(remaining).hasSize(1);
    assertThat(remaining.get(0).getId()).isEqualTo(recentPing.getId());
  }

  @Test
  @Transactional
  void shouldApplyEachPlansRetentionWindow() {
    Service free = serviceOnPlan(PlanType.FREE, "free-svc");
    Service pro = serviceOnPlan(PlanType.PRO, "pro-svc");
    ServicePing freeOld = pingAgedDays(free, 2); // past Free's 1 day
    ServicePing proOld = pingAgedDays(pro, 2); // within Pro's 30 days
    ServicePing proAncient = pingAgedDays(pro, 31);

    pingRetentionService.cleanupOldPings();

    assertThat(servicePingRepository.findById(freeOld.getId())).isEmpty();
    assertThat(servicePingRepository.findById(proOld.getId())).isPresent();
    assertThat(servicePingRepository.findById(proAncient.getId())).isEmpty();
  }

  private Service serviceOnPlan(PlanType plan, String name) {
    Organization org = organizationRepository.save(new Organization(UUID.randomUUID(), name + " org"));
    Subscription subscription = new Subscription(UUID.randomUUID(), org);
    subscription.setPlanType(plan);
    subscriptionRepository.save(subscription);
    return serviceRepository.save(
        new Service(UUID.randomUUID(), org, name, null, null, "global", "key-" + name));
  }

  private ServicePing pingAgedDays(Service service, int days) {
    ServicePing ping =
        servicePingRepository.saveAndFlush(
            new ServicePing(UUID.randomUUID(), service, ServiceStatus.HEALTHY, 10, null));
    entityManager
        .createNativeQuery("update service_pings set created_at = :at where id = :id")
        .setParameter("at", OffsetDateTime.now().minusDays(days))
        .setParameter("id", ping.getId())
        .executeUpdate();
    return ping;
  }
}
