package com.servicedna.alert.controller;

import com.servicedna.alert.dto.AlertRuleDto;
import com.servicedna.alert.dto.CreateAlertRuleRequest;
import com.servicedna.alert.service.AlertRuleService;
import com.servicedna.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/services/{serviceId}/alert-rules")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    public AlertRuleController(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @PostMapping
    public ResponseEntity<AlertRuleDto> createAlertRule(
            @PathVariable UUID orgId,
            @PathVariable UUID serviceId,
            @Valid @RequestBody CreateAlertRuleRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return new ResponseEntity<>(
                alertRuleService.createAlertRule(orgId, serviceId, request, userDetails.getUser().getId()),
                HttpStatus.CREATED
        );
    }

    @GetMapping
    public ResponseEntity<List<AlertRuleDto>> getAlertRules(
            @PathVariable UUID orgId,
            @PathVariable UUID serviceId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(alertRuleService.getAlertRules(orgId, serviceId, userDetails.getUser().getId()));
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteAlertRule(
            @PathVariable UUID orgId,
            @PathVariable UUID serviceId, // included for URL consistency
            @PathVariable UUID ruleId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        alertRuleService.deleteAlertRule(orgId, ruleId, userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }
}
