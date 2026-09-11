package com.servicedna.webhook.domain;

import com.servicedna.organization.domain.Organization;
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
@Table(name = "organization_webhooks")
public class OrganizationWebhook {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @Column(nullable = false, length = 2048)
  private String url;

  @Enumerated(EnumType.STRING)
  @Column(name = "webhook_type", nullable = false)
  private WebhookType webhookType;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  public OrganizationWebhook() {}

  public OrganizationWebhook(UUID id, Organization organization, String url, WebhookType webhookType) {
    this.id = id;
    this.organization = organization;
    this.url = url;
    this.webhookType = webhookType;
  }

  public UUID getId() {
    return id;
  }

  public Organization getOrganization() {
    return organization;
  }

  public String getUrl() {
    return url;
  }

  public WebhookType getWebhookType() {
    return webhookType;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
