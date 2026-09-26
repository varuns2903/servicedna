package com.servicedna.catalog.dto;

import com.servicedna.graph.domain.Protocol;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class CatalogDto {

  private CatalogDto() {}

  /**
   * What `sdna scan` found in a repository. {@code operations} replace the service's previous
   * catalog (null leaves it as is, for scans that only found configuration); {@code dependencies}
   * are service names found in its configuration, added as declared edges.
   */
  public record ScanRequest(
      @NotBlank @Size(max = 255) String service,
      @Size(max = 64) String environment,
      @Size(max = 2000) List<@Valid OperationSpec> operations,
      @NotNull @Size(max = 200) List<@NotBlank @Size(max = 255) String> dependencies) {}

  public record OperationSpec(
      @NotNull Protocol protocol,
      @NotBlank @Size(max = 255) String name,
      @NotBlank @Size(max = 16) String source,
      @Size(max = 2000) String description,
      @Size(max = 65536) String requestSchema) {}

  public record ScanResult(
      UUID serviceId, int operations, List<String> dependenciesAdded, List<String> dependenciesUnknown) {}

  /** A service's operations: from its specs, and/or seen in traffic (with how often). */
  public record Operation(
      Protocol protocol,
      String name,
      String source,
      String description,
      String requestSchema,
      boolean observed,
      long callsLast24h) {}
}
