package com.servicedna.telemetry.service;

import com.servicedna.alert.event.ServiceStatusChangedEvent;
import com.servicedna.alert.service.AlertEventPublisher;
import com.servicedna.dashboard.event.DashboardInvalidationEvent;
import com.servicedna.service.domain.Service;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.repository.ServiceRepository;
import com.servicedna.telemetry.domain.ServicePing;
import com.servicedna.telemetry.repository.ServicePingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The single place a health observation turns into a service status change, shared by the active
 * prober ({@link HealthCheckProberService}) and pushed pings ({@link PingService}).
 *
 * <p>Both sources report the same service concurrently, which caused two problems when each did
 * its own read-compare-write: two observations of the same change could both see the old status
 * and both publish an alert, and when the sources disagreed (e.g. near the DEGRADED latency
 * threshold) the status flipped on every report. This class fixes both: the compare-and-set runs
 * under a row lock, and a change only applies once enough consecutive observations agree.
 */
@Component
public class ServiceStatusRecorder {

  private static final Logger log = LoggerFactory.getLogger(ServiceStatusRecorder.class);
  private static final String[] SERVICE_CACHES = {"services", "publicStatus"};

  private final EntityManager entityManager;
  private final ServiceRepository serviceRepository;
  private final ServicePingRepository servicePingRepository;
  private final AlertEventPublisher alertEventPublisher;
  private final ApplicationEventPublisher eventPublisher;
  private final CacheManager cacheManager;
  private final int confirmations;

  public ServiceStatusRecorder(
      EntityManager entityManager,
      ServiceRepository serviceRepository,
      ServicePingRepository servicePingRepository,
      @Lazy AlertEventPublisher alertEventPublisher,
      ApplicationEventPublisher eventPublisher,
      CacheManager cacheManager,
      @Value("${health-check.status-confirmations:2}") int confirmations) {
    this.entityManager = entityManager;
    this.serviceRepository = serviceRepository;
    this.servicePingRepository = servicePingRepository;
    this.alertEventPublisher = alertEventPublisher;
    this.eventPublisher = eventPublisher;
    this.cacheManager = cacheManager;
    this.confirmations = Math.max(1, confirmations);
  }

  /**
   * Records one observation and applies the status change if it's confirmed.
   *
   * @return whether the service's status changed
   */
  @Transactional
  public boolean record(
      UUID serviceId, ServiceStatus observed, Integer latencyMs, String message) {
    Service service = entityManager.find(Service.class, serviceId);
    if (service == null) {
      return false;
    }
    // Re-read under a row lock even if the entity was already loaded in this persistence context
    // (e.g. by the caller): a plain locking query would hand back that possibly-stale instance.
    entityManager.refresh(service, LockModeType.PESSIMISTIC_WRITE);

    servicePingRepository.save(
        new ServicePing(UUID.randomUUID(), service, observed, latencyMs, message));

    ServiceStatus oldStatus = service.getStatus();
    if (oldStatus == observed || !isConfirmed(service, observed)) {
      return false;
    }

    service.setStatus(observed);
    serviceRepository.save(service);

    UUID organizationId = service.getOrganization().getId();
    runAfterCommit(() -> notifyStatusChanged(serviceId, organizationId, oldStatus, observed));
    return true;
  }

  /**
   * A change sticks only once the last {@code confirmations} observations (from any source) all
   * agree on it. A service's first status after registration applies immediately, so new services
   * don't sit at UNKNOWN.
   */
  private boolean isConfirmed(Service service, ServiceStatus observed) {
    if (confirmations == 1 || service.getStatus() == ServiceStatus.UNKNOWN) {
      return true;
    }
    List<ServicePing> recent =
        servicePingRepository.findByServiceIdOrderByCreatedAtDesc(
            service.getId(), PageRequest.of(0, confirmations));
    return recent.size() == confirmations
        && recent.stream().allMatch(ping -> ping.getStatus() == observed);
  }

  /**
   * Runs after the status write commits, so listeners and cache readers never see the change
   * before it's in the database — evicting earlier would let a concurrent read re-cache the old
   * status for the full cache TTL. A failure here (e.g. Kafka unreachable) must not undo the
   * already-committed status change.
   */
  private void notifyStatusChanged(
      UUID serviceId, UUID organizationId, ServiceStatus oldStatus, ServiceStatus newStatus) {
    evictServiceCaches();
    try {
      alertEventPublisher.publishStatusChangedEvent(
          new ServiceStatusChangedEvent(
              serviceId, organizationId, oldStatus, newStatus, OffsetDateTime.now()));
      eventPublisher.publishEvent(new DashboardInvalidationEvent(this, organizationId));
    } catch (Exception e) {
      log.warn(
          "Status for service {} updated to {} but notifying downstream listeners failed: {}",
          serviceId,
          newStatus,
          e.getMessage());
    }
  }

  private void evictServiceCaches() {
    for (String cacheName : SERVICE_CACHES) {
      Cache cache = cacheManager.getCache(cacheName);
      if (cache != null) {
        cache.clear();
      }
    }
  }

  private static void runAfterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }
}
