package com.servicedna.graph.event;

import com.servicedna.graph.domain.Protocol;
import java.util.UUID;

/** A service called another service for the first time ({@code operation} is the call it made). */
public record NewServiceEdgeEvent(UUID organizationId, UUID sourceServiceId, UUID targetServiceId, String operation, Protocol protocol) {}
