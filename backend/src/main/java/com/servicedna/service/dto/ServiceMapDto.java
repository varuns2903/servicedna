package com.servicedna.service.dto;

import com.servicedna.service.domain.ServiceStatus;
import java.util.List;
import java.util.UUID;

public record ServiceMapDto(
        List<ServiceNodeDto> nodes,
        List<ServiceEdgeDto> edges
) {
    public record ServiceNodeDto(
            UUID id,
            String name,
            String region,
            ServiceStatus status
    ) {}

    public record ServiceEdgeDto(
            UUID sourceId,
            UUID targetId
    ) {}
}
