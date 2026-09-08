package com.servicedna.organization.event;

import com.servicedna.organization.service.AuditLogService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AuditLogEventListener {

  private final AuditLogService auditLogService;

  public AuditLogEventListener(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  @Async
  @EventListener
  public void handleAuditLogEvent(AuditLogEvent event) {
    auditLogService.logAction(
        event.organizationId(),
        event.userId(),
        event.action(),
        event.entityType(),
        event.entityId(),
        event.details(),
        event.ipAddress());
  }
}
