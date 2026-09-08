package com.servicedna.dashboard.event;

import com.servicedna.dashboard.dto.DashboardSummaryDto;
import com.servicedna.dashboard.service.DashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class DashboardWebSocketPublisher {

  private static final Logger log = LoggerFactory.getLogger(DashboardWebSocketPublisher.class);

  private final DashboardService dashboardService;
  private final SimpMessagingTemplate messagingTemplate;

  public DashboardWebSocketPublisher(
      DashboardService dashboardService, SimpMessagingTemplate messagingTemplate) {
    this.dashboardService = dashboardService;
    this.messagingTemplate = messagingTemplate;
  }

  @EventListener
  public void handleDashboardInvalidation(DashboardInvalidationEvent event) {
    log.info(
        "Invalidating dashboard cache and broadcasting update for Org: {}",
        event.getOrganizationId());

    // 1. Evict the old cached view
    dashboardService.invalidateDashboardCache(event.getOrganizationId());

    // 2. Compute the new summary directly from the DB
    DashboardSummaryDto newSummary =
        dashboardService.getDashboardSummaryInternal(event.getOrganizationId());

    // 3. Push it live to all connected WebSocket clients subscribed to this org's topic
    String destination = "/topic/organizations/" + event.getOrganizationId() + "/dashboard";
    messagingTemplate.convertAndSend(destination, newSummary);
    log.debug("Published live dashboard update to {}", destination);
  }
}
