package com.servicedna.testrun;

import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.event.AuditLogEvent;
import com.servicedna.organization.service.OrganizationService;
import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether test runs may be sent to an environment. Allowed by default, except in
 * production-like environments (prod, production, live…), which an owner or admin must opt in.
 */
@org.springframework.stereotype.Service
public class EnvironmentSettingsService {

  private static final Pattern PRODUCTION_LIKE = Pattern.compile("(?i)^(prod|production|prd|live)([-_].*)?$");

  private final EnvironmentSettingsRepository repository;
  private final ServiceRepository serviceRepository;
  private final OrganizationService organizationService;
  private final ApplicationEventPublisher events;

  public EnvironmentSettingsService(
      EnvironmentSettingsRepository repository,
      ServiceRepository serviceRepository,
      OrganizationService organizationService,
      ApplicationEventPublisher events) {
    this.repository = repository;
    this.serviceRepository = serviceRepository;
    this.organizationService = organizationService;
    this.events = events;
  }

  public record Setting(String environment, boolean productionLike, boolean allowTestRuns) {}

  static boolean productionLike(String environment) {
    return environment != null && PRODUCTION_LIKE.matcher(environment).matches();
  }

  @Transactional(readOnly = true)
  public boolean testRunsAllowed(UUID organizationId, String environment) {
    if (environment == null) {
      return true;
    }
    return repository.findById(new EnvironmentSettings.Key(organizationId, environment))
        .map(EnvironmentSettings::isAllowTestRuns)
        .orElse(!productionLike(environment));
  }

  /** Every environment the organization's services report, with its effective setting. */
  @Transactional(readOnly = true)
  public List<Setting> list(UUID organizationId, UUID userId) {
    organizationService.validateUserAccess(organizationId, userId);
    TreeSet<String> environments = new TreeSet<>();
    serviceRepository.findByOrganizationId(organizationId).stream().map(Service::getEnvironment).filter(Objects::nonNull).forEach(environments::add);
    repository.findByIdOrganizationId(organizationId).forEach(s -> environments.add(s.getId().environment()));
    return environments.stream().map(env -> new Setting(env, productionLike(env), testRunsAllowed(organizationId, env))).toList();
  }

  @Transactional
  public Setting update(UUID organizationId, String environment, boolean allowTestRuns, UUID userId) {
    OrganizationMember member = organizationService.validateUserAccess(organizationId, userId);
    if (member.getRole() != OrganizationRole.OWNER && member.getRole() != OrganizationRole.ADMIN) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Only owners and admins can change environment settings.");
    }
    EnvironmentSettings settings = repository.findById(new EnvironmentSettings.Key(organizationId, environment))
        .orElseGet(() -> new EnvironmentSettings(organizationId, environment));
    settings.setAllowTestRuns(allowTestRuns);
    settings.setUpdatedBy(userId);
    repository.save(settings);
    events.publishEvent(new AuditLogEvent(organizationId, userId, allowTestRuns ? "ALLOW_TEST_RUNS" : "DISALLOW_TEST_RUNS",
        "Environment", environment, (allowTestRuns ? "Allowed" : "Disallowed") + " test runs in " + environment, null));
    return new Setting(environment, productionLike(environment), allowTestRuns);
  }
}
