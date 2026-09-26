package com.servicedna.graph.service;

import com.servicedna.graph.domain.SeenServiceEdge;
import com.servicedna.graph.event.NewServiceEdgeEvent;
import com.servicedna.graph.repository.SeenServiceEdgeRepository;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Remembers which services have called which, and announces a pair the first time it's seen.
 * Known pairs are kept in memory, so steady traffic costs no queries.
 */
@Component
public class ServiceEdgeTracker {

  private final SeenServiceEdgeRepository repository;
  private final TransactionTemplate transactions;
  private final ApplicationEventPublisher events;
  private final Set<SeenServiceEdge.Key> known = ConcurrentHashMap.newKeySet();

  public ServiceEdgeTracker(SeenServiceEdgeRepository repository, TransactionTemplate transactions, ApplicationEventPublisher events) {
    this.repository = repository;
    this.transactions = transactions;
    this.events = events;
  }

  public void seen(NewServiceEdgeEvent edge) {
    if (edge.sourceServiceId().equals(edge.targetServiceId())) {
      return;
    }
    SeenServiceEdge.Key key = new SeenServiceEdge.Key(edge.sourceServiceId(), edge.targetServiceId());
    if (known.contains(key)) {
      return;
    }
    boolean isNew;
    try {
      isNew = Boolean.TRUE.equals(transactions.execute(status -> {
        if (repository.existsById(key)) {
          return false;
        }
        repository.saveAndFlush(new SeenServiceEdge(edge.organizationId(), edge.sourceServiceId(), edge.targetServiceId(), OffsetDateTime.now()));
        return true;
      }));
    } catch (DataIntegrityViolationException e) {
      isNew = false; // another instance recorded it first, and announces it
    }
    known.add(key);
    if (isNew) {
      events.publishEvent(edge);
    }
  }
}
