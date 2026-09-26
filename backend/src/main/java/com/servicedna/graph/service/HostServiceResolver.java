package com.servicedna.graph.service;

import com.servicedna.service.domain.Service;
import com.servicedna.service.repository.ServiceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Maps a hostname from a client span to a registered service with that name — "product-service",
 * "product-service.shop.svc.cluster.local" and "product-service:4002" all match
 * {@code product-service}. Prefers a service in the caller's environment. Lookups are cached
 * briefly per organization.
 */
@Component
public class HostServiceResolver {

  private static final Duration CACHE_TTL = Duration.ofMinutes(1);

  private record Cached(List<Service> services, Instant at) {}

  private final ServiceRepository serviceRepository;
  private final Map<UUID, Cached> byOrganization = new ConcurrentHashMap<>();

  public HostServiceResolver(ServiceRepository serviceRepository) {
    this.serviceRepository = serviceRepository;
  }

  public Optional<UUID> byHostname(UUID organizationId, String host, String callerEnvironment) {
    String name = host.split("[.:]", 2)[0];
    if (name.isEmpty()) {
      return Optional.empty();
    }
    List<Service> candidates =
        services(organizationId).stream().filter(s -> s.getName().equalsIgnoreCase(name)).toList();
    return candidates.stream()
        .filter(s -> Objects.equals(s.getEnvironment(), callerEnvironment))
        .findFirst()
        .or(() -> candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty())
        .map(Service::getId);
  }

  private List<Service> services(UUID organizationId) {
    Instant now = Instant.now();
    Cached cached = byOrganization.get(organizationId);
    if (cached == null || cached.at().isBefore(now.minus(CACHE_TTL))) {
      cached = new Cached(serviceRepository.findByOrganizationId(organizationId), now);
      byOrganization.put(organizationId, cached);
    }
    return cached.services();
  }
}
