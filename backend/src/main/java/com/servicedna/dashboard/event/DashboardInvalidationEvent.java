package com.servicedna.dashboard.event;

import java.util.UUID;
import org.springframework.context.ApplicationEvent;

public class DashboardInvalidationEvent extends ApplicationEvent {
  private final UUID organizationId;

  public DashboardInvalidationEvent(Object source, UUID organizationId) {
    super(source);
    this.organizationId = organizationId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }
}
