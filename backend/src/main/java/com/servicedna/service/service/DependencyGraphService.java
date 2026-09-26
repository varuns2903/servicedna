package com.servicedna.service.service;

import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
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

/** Walks an organization's declared service dependency graph. */
@org.springframework.stereotype.Service
public class DependencyGraphService {

  private final ServiceRepository serviceRepository;

  public DependencyGraphService(ServiceRepository serviceRepository) {
    this.serviceRepository = serviceRepository;
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
        dependsOn.computeIfAbsent(service.getId(), id -> new HashSet<>()).add(dependency.getId());
        dependedOnBy.computeIfAbsent(dependency.getId(), id -> new HashSet<>()).add(service.getId());
      }
    }

    return new Relations(
        reachable(serviceId, id -> dependsOn.getOrDefault(id, Set.of())),
        reachable(serviceId, id -> dependedOnBy.getOrDefault(id, Set.of())));
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
