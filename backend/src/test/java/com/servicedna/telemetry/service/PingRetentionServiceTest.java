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
}
