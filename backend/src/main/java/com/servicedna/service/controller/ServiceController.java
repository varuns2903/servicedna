package com.servicedna.service.controller;

import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.service.dto.AddDependencyRequest;
import com.servicedna.service.dto.CreateServiceRequest;
import com.servicedna.service.dto.ServiceDto;
import com.servicedna.service.dto.UpdateServiceStatusRequest;
import com.servicedna.service.service.ServiceRegistryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/services")
public class ServiceController {

    private final ServiceRegistryService serviceRegistryService;

    public ServiceController(ServiceRegistryService serviceRegistryService) {
        this.serviceRegistryService = serviceRegistryService;
    }

    @PostMapping
    public ResponseEntity<ServiceDto> createService(
            @PathVariable UUID orgId,
            @Valid @RequestBody CreateServiceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return new ResponseEntity<>(
                serviceRegistryService.createService(orgId, request, userDetails.getUser().getId()),
                HttpStatus.CREATED
        );
    }

    @GetMapping
    public ResponseEntity<List<ServiceDto>> getServices(
            @PathVariable UUID orgId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(serviceRegistryService.getServices(orgId, userDetails.getUser().getId()));
    }

    @GetMapping("/{serviceId}")
    public ResponseEntity<ServiceDto> getService(
            @PathVariable UUID orgId,
            @PathVariable UUID serviceId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(serviceRegistryService.getService(orgId, serviceId, userDetails.getUser().getId()));
    }

    @GetMapping("/map")
    public ResponseEntity<com.servicedna.service.dto.ServiceMapDto> getServiceMap(
            @PathVariable UUID orgId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(serviceRegistryService.getServiceMap(orgId, userDetails.getUser().getId()));
    }
    
    @PatchMapping("/{serviceId}/status")
    public ResponseEntity<ServiceDto> updateServiceStatus(
            @PathVariable UUID orgId,
            @PathVariable UUID serviceId,
            @Valid @RequestBody UpdateServiceStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(serviceRegistryService.updateServiceStatus(orgId, serviceId, request, userDetails.getUser().getId()));
    }

    @PostMapping("/{serviceId}/dependencies")
    public ResponseEntity<ServiceDto> addDependency(
            @PathVariable UUID orgId,
            @PathVariable UUID serviceId,
            @Valid @RequestBody AddDependencyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(serviceRegistryService.addDependency(orgId, serviceId, request, userDetails.getUser().getId()));
    }
}
