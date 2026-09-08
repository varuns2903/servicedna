package com.servicedna.telemetry.domain;

import com.servicedna.service.domain.Service;
import com.servicedna.service.domain.ServiceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "service_pings")
public class ServicePing {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "service_id", nullable = false)
  private Service service;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ServiceStatus status;

  @Column(name = "latency_ms")
  private Integer latencyMs;

  @Column(columnDefinition = "TEXT")
  private String message;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  public ServicePing() {}

  public ServicePing(
      UUID id, Service service, ServiceStatus status, Integer latencyMs, String message) {
    this.id = id;
    this.service = service;
    this.status = status;
    this.latencyMs = latencyMs;
    this.message = message;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Service getService() {
    return service;
  }

  public void setService(Service service) {
    this.service = service;
  }

  public ServiceStatus getStatus() {
    return status;
  }

  public void setStatus(ServiceStatus status) {
    this.status = status;
  }

  public Integer getLatencyMs() {
    return latencyMs;
  }

  public void setLatencyMs(Integer latencyMs) {
    this.latencyMs = latencyMs;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
