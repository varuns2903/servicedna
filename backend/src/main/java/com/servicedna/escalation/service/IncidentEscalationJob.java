package com.servicedna.escalation.service;

import com.servicedna.common.mail.MailService;
import com.servicedna.escalation.domain.EscalationPolicy;
import com.servicedna.escalation.repository.EscalationPolicyRepository;
import com.servicedna.incident.domain.Incident;
import com.servicedna.incident.domain.IncidentSeverity;
import com.servicedna.incident.domain.IncidentStatus;
import com.servicedna.incident.repository.IncidentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pages a fallback contact when a CRITICAL/MAJOR incident goes unacknowledged past the org's
 * configured window — without this, on-call paging (see IncidentService.notifyOnCallIfSevere) is
 * a single attempt with no fallback if that person misses it.
 */
@Component
public class IncidentEscalationJob {

  private static final Logger log = LoggerFactory.getLogger(IncidentEscalationJob.class);
  private static final List<IncidentSeverity> ESCALATABLE_SEVERITIES =
      List.of(IncidentSeverity.CRITICAL, IncidentSeverity.MAJOR);

  private final EscalationPolicyRepository escalationPolicyRepository;
  private final IncidentRepository incidentRepository;
  private final MailService mailService;
  private final String frontendUrl;

  public IncidentEscalationJob(
      EscalationPolicyRepository escalationPolicyRepository,
      IncidentRepository incidentRepository,
      MailService mailService,
      @Value("${frontend.url}") String frontendUrl) {
    this.escalationPolicyRepository = escalationPolicyRepository;
    this.incidentRepository = incidentRepository;
    this.mailService = mailService;
    this.frontendUrl = frontendUrl;
  }

  @Scheduled(cron = "${ESCALATION_CHECK_CRON:0 */5 * * * *}")
  @Transactional
  public void escalateUnacknowledgedIncidents() {
    for (EscalationPolicy policy : escalationPolicyRepository.findAll()) {
      OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(policy.getEscalateAfterMinutes());

      List<Incident> toEscalate =
          incidentRepository
              .findByOrganizationIdAndStatusNotAndAcknowledgedAtIsNullAndEscalatedAtIsNullAndSeverityInAndCreatedAtBefore(
                  policy.getOrganization().getId(), IncidentStatus.RESOLVED, ESCALATABLE_SEVERITIES, cutoff);

      for (Incident incident : toEscalate) {
        String link = frontendUrl + "/incidents/" + incident.getId();
        mailService.send(
            policy.getEscalationEmail(),
            "[ESCALATED] " + incident.getTitle(),
            "This "
                + incident.getSeverity()
                + " incident has gone unacknowledged for "
                + policy.getEscalateAfterMinutes()
                + " minutes and is being escalated to you:\n\n"
                + incident.getTitle()
                + "\n\n"
                + incident.getDescription()
                + "\n\n"
                + link);
        incident.setEscalatedAt(OffsetDateTime.now());
        incidentRepository.save(incident);
        log.info("Escalated incident {} to {}", incident.getId(), policy.getEscalationEmail());
      }
    }
  }
}
