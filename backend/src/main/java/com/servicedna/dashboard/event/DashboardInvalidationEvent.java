package com.servicedna.dashboard.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

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
