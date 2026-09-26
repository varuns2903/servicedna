package com.servicedna.service.service;

import com.servicedna.graph.repository.ObservedCallRepository;
import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.transaction.annotation.Transactional;

/**
 * Walks an organization's service dependency graph: declared edges plus service-to-service calls
 * observed in traffic over the last day.
 */
@org.springframework.stereotype.Service
public class DependencyGraphService {

  static final Duration OBSERVED_LOOKBACK = Duration.ofHours(24);

  private final ServiceRepository serviceRepository;
  private final ObservedCallRepository observedCallRepository;

  public DependencyGraphService(
      ServiceRepository serviceRepository, ObservedCallRepository observedCallRepository) {
    this.serviceRepository = serviceRepository;
    this.observedCallRepository = observedCallRepository;
  }

  /**
   * What a service transitively depends on, and what transitively depends on it. Deliberately
   * not the whole connected component: two services that merely share a distant neighbour aren't
   * related to each other.
   */
  public record Relations(Set<UUID> dependencies, Set<UUID> dependents) {}

  @Transactional(readOnly = true)
  public Relations relationsOf(UUID organizationId, UUID serviceId) {
    List<Service> services = serviceRepository.findByOrganizationId(organizationId);

    Map<UUID, Set<UUID>> dependsOn = new HashMap<>();
    Map<UUID, Set<UUID>> dependedOnBy = new HashMap<>();
    for (Service service : services) {
      for (Service dependency : service.getDependencies()) {
        addEdge(dependsOn, dependedOnBy, service.getId(), dependency.getId());
      }
    }
    OffsetDateTime since = OffsetDateTime.now().minus(OBSERVED_LOOKBACK);
    for (Object[] edge : observedCallRepository.findServiceEdgesSince(organizationId, since)) {
      if (!edge[0].equals(edge[1])) {
        addEdge(dependsOn, dependedOnBy, (UUID) edge[0], (UUID) edge[1]);
      }
    }

    return new Relations(
        reachable(serviceId, id -> dependsOn.getOrDefault(id, Set.of())),
        reachable(serviceId, id -> dependedOnBy.getOrDefault(id, Set.of())));
  }

  private static void addEdge(
      Map<UUID, Set<UUID>> dependsOn, Map<UUID, Set<UUID>> dependedOnBy, UUID from, UUID to) {
    dependsOn.computeIfAbsent(from, id -> new HashSet<>()).add(to);
    dependedOnBy.computeIfAbsent(to, id -> new HashSet<>()).add(from);
  }

  private static Set<UUID> reachable(UUID start, Function<UUID, Set<UUID>> edges) {
    Set<UUID> seen = new HashSet<>();
    Deque<UUID> queue = new ArrayDeque<>(edges.apply(start));
    while (!queue.isEmpty()) {
      UUID next = queue.poll();
      if (!next.equals(start) && seen.add(next)) {
        queue.addAll(edges.apply(next));
      }
    }
    return seen;
  }
}
