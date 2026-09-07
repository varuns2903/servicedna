package com.servicedna.incident.dto;

import jakarta.validation.constraints.NotBlank;

public record UpsertPostMortemRequest(
        @NotBlank(message = "Root cause is required")
        String rootCause,
        
        @NotBlank(message = "Timeline is required")
        String timeline,
        
        @NotBlank(message = "Action items are required")
        String actionItems
) {}
