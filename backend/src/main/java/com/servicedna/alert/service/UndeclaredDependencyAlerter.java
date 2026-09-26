package com.servicedna.alert.service;

import com.servicedna.alert.domain.AlertCondition;
import com.servicedna.alert.domain.AlertRule;
import com.servicedna.alert.repository.AlertRuleRepository;
import com.servicedna.graph.event.NewServiceEdgeEvent;
import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Alerts when a service starts calling a service it doesn't declare as a dependency — a hidden
 * coupling that the declared graph (and whoever relies on it for impact analysis) doesn't know
 * about. Fires the source service's UNDECLARED_DEPENDENCY rules.
 */
@Component
public class UndeclaredDependencyAlerter {

  private final AlertRuleRepository rules;
  private final ServiceRepository services;
  private final AlertActionService actions;
  private final TransactionTemplate readOnly;

  public UndeclaredDependencyAlerter(AlertRuleRepository rules, ServiceRepository services, AlertActionService actions,
      PlatformTransactionManager transactionManager) {
    this.rules = rules;
    this.services = services;
    this.actions = actions;
    this.readOnly = new TransactionTemplate(transactionManager);
    this.readOnly.setReadOnly(true);
  }

  private record Edge(String source, String target, boolean declared) {}

  // Not transactional itself: the incident it may open is written in its own transaction.
  @EventListener
  public void onNewEdge(NewServiceEdgeEvent edge) {
    List<AlertRule> matching = rules.findByServiceIdAndCondition(edge.sourceServiceId(), AlertCondition.UNDECLARED_DEPENDENCY);
    if (matching.isEmpty()) {
      return;
    }
    Edge names = readOnly.execute(status -> {
      Service source = services.findById(edge.sourceServiceId()).orElse(null);
      Service target = services.findById(edge.targetServiceId()).orElse(null);
      if (source == null || target == null) {
        return null;
      }
      return new Edge(source.getName(), target.getName(), source.getDependencies().stream().anyMatch(d -> d.getId().equals(target.getId())));
    });
    if (names == null || names.declared()) {
      return;
    }
    String call = edge.operation() == null || edge.operation().isBlank() ? edge.protocol().name() : edge.protocol() + " " + edge.operation();
    String message = names.source() + " started calling " + names.target() + " (" + call + "), which isn't a declared dependency.";
    actions.sendWebhooks(matching, message, new AlertActionService.AlertContext(names.source(), null, null, null));
    actions.openIncident(matching, edge.organizationId(), edge.sourceServiceId(), names.source(),
        "calls " + names.target() + ", an undeclared dependency", message);
  }
}
