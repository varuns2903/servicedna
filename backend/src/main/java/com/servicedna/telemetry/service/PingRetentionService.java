package com.servicedna.telemetry.service;

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
 * Runs daily and deletes anything older than the configured retention window.
 */
@Component
public class PingRetentionService {

  private static final Logger log = LoggerFactory.getLogger(PingRetentionService.class);

  private final ServicePingRepository servicePingRepository;
  private final int retentionDays;

  public PingRetentionService(
      ServicePingRepository servicePingRepository,
      @Value("${PING_RETENTION_DAYS:90}") int retentionDays) {
    this.servicePingRepository = servicePingRepository;
    this.retentionDays = retentionDays;
  }

  @Scheduled(cron = "${PING_RETENTION_CRON:0 0 3 * * *}")
  @Transactional
  public void cleanupOldPings() {
    OffsetDateTime cutoff = OffsetDateTime.now().minusDays(retentionDays);
    int deleted = servicePingRepository.deleteByCreatedAtBefore(cutoff);
    if (deleted > 0) {
      log.info("Deleted {} service_pings rows older than {} days", deleted, retentionDays);
    }
  }
}
