package com.servicedna.telemetry.service;

import com.servicedna.billing.domain.PlanType;
import com.servicedna.billing.service.PlanLimitService;
import com.servicedna.telemetry.repository.ServicePingRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every active health-check probe (every 30s per monitored service, see
 * HealthCheckProberService) and every pushed ping writes a service_pings row. Without pruning,
 * that grows unbounded — a single actively-monitored service alone accumulates ~2,880 rows/day.
 * Runs daily and deletes anything older than the retention window: each organization's plan
 * limit ({@link PlanType#getPingRetentionDays()}), capped at PING_RETENTION_DAYS for everyone.
 */
@Component
public class PingRetentionService {

  private static final Logger log = LoggerFactory.getLogger(PingRetentionService.class);

  private final ServicePingRepository servicePingRepository;
  private final PlanLimitService planLimitService;
  private final int retentionDays;

  public PingRetentionService(
      ServicePingRepository servicePingRepository,
      PlanLimitService planLimitService,
      @Value("${PING_RETENTION_DAYS:90}") int retentionDays) {
    this.servicePingRepository = servicePingRepository;
    this.planLimitService = planLimitService;
    this.retentionDays = retentionDays;
  }

  @Scheduled(cron = "${PING_RETENTION_CRON:0 0 3 * * *}")
  @Transactional
  public void cleanupOldPings() {
    OffsetDateTime now = OffsetDateTime.now();
    int deleted = 0;
    if (planLimitService.isEnforced()) {
      for (PlanType plan : PlanType.values()) {
        int days = planLimitService.retentionDays(plan);
        if (days < retentionDays) {
          deleted += servicePingRepository.deleteForPlanOlderThan(plan, now.minusDays(days));
        }
      }
    }
    deleted += servicePingRepository.deleteByCreatedAtBefore(now.minusDays(retentionDays));
    if (deleted > 0) {
      log.info("Deleted {} service_pings rows past their plan's retention window", deleted);
    }
  }
}
