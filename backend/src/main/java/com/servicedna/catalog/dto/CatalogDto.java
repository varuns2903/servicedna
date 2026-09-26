package com.servicedna.catalog.dto;

import com.servicedna.graph.domain.Protocol;
import jakarta.validation.Valid;
import com.servicedna.alert.dto.CreateAlertRuleRequest;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
      @NotNull @Size(max = 200) List<@NotBlank @Size(max = 255) String> dependencies,
      /** From servicedna.yaml; null fields are left as they are. */
      @Valid Metadata metadata,
      /** servicedna.yaml's alert rules, replacing the ones it set before; null leaves them alone. */
      @Size(max = 50) List<@Valid CreateAlertRuleRequest> alerts) {

    public ScanRequest(String service, String environment, List<OperationSpec> operations, List<String> dependencies) {
      this(service, environment, operations, dependencies, null, null);
    }
  }

  /** What servicedna.yaml says about a service that traffic can't reveal. */
  public record Metadata(
      @Size(max = 2000) String description,
      @Size(max = 255) String owner,
      @Pattern(regexp = "critical|high|medium|low", message = "tier is critical, high, medium or low") String tier,
      @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") Double slo,
      @Size(max = 2048) @Pattern(regexp = "https?://.+", message = "health must be a full http(s) URL") String healthUrl,
      @Size(max = 2048) String repositoryUrl) {}

  public record OperationSpec(
      @NotNull Protocol protocol,
      @NotBlank @Size(max = 255) String name,
      @NotBlank @Size(max = 16) String source,
      @Size(max = 2000) String description,
      @Size(max = 65536) String requestSchema) {}

  public record ScanResult(
      UUID serviceId, int operations, List<String> dependenciesAdded, List<String> dependenciesUnknown,
      /** Metadata fields servicedna.yaml set. */
      List<String> updated,
      /** How many alert rules servicedna.yaml now manages; null when it declared none. */
      Integer alertRules) {}

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
