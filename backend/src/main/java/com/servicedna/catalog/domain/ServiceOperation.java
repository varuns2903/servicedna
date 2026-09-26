package com.servicedna.catalog.domain;

import com.servicedna.graph.domain.Protocol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** An operation a service's own specs declare: "POST /orders", "Payment/Charge", "consume order.created". */
@Entity
@Table(name = "service_operations")
public class ServiceOperation {

  @Id private UUID id;

  @Column(name = "service_id", nullable = false)
  private UUID serviceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Protocol protocol;

  @Column(nullable = false)
  private String name;

  /** OPENAPI, PROTO or ASYNCAPI. */
  @Column(nullable = false, length = 16)
  private String source;

  @Column(columnDefinition = "TEXT")
  private String description;

  /** JSON Schema of the request body/message, as the spec gives it. */
  @Column(name = "request_schema", columnDefinition = "TEXT")
  private String requestSchema;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  public ServiceOperation() {}

  public ServiceOperation(
      UUID serviceId, Protocol protocol, String name, String source, String description, String requestSchema) {
    this.id = UUID.randomUUID();
    this.serviceId = serviceId;
    this.protocol = protocol;
    this.name = name;
    this.source = source;
    this.description = description;
    this.requestSchema = requestSchema;
  }

  public UUID getServiceId() {
    return serviceId;
  }

  public Protocol getProtocol() {
    return protocol;
  }

  public String getName() {
    return name;
  }

  public String getSource() {
    return source;
  }

  public String getDescription() {
    return description;
  }

  public String getRequestSchema() {
    return requestSchema;
  }
}
