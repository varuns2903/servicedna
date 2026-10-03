package com.servicedna.github;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/** A row of {@link GitHubPollHeads} (which reads and writes them); mapped so the schema is checked. */
@Entity
@Table(name = "github_poll_heads")
@IdClass(GitHubPollHead.Key.class)
class GitHubPollHead {

  public static class Key implements Serializable {
    private Long installationId;
    private String repository;
    private String ref;

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && Objects.equals(installationId, k.installationId) && Objects.equals(repository, k.repository)
          && Objects.equals(ref, k.ref);
    }

    @Override
    public int hashCode() {
      return Objects.hash(installationId, repository, ref);
    }
  }

  @Id
  @Column(name = "installation_id")
  private Long installationId;

  @Id
  @Column(name = "repository")
  private String repository;

  @Id
  @Column(name = "ref", length = 64)
  private String ref;

  @Column(name = "sha", nullable = false)
  private String sha;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected GitHubPollHead() {}
}
