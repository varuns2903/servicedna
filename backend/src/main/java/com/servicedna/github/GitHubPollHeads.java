package com.servicedna.github;

import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** What polling last saw of each repository (see V43); safe to share between ServiceDNA instances. */
@Component
class GitHubPollHeads {

  private final JdbcTemplate jdbc;

  GitHubPollHeads(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Records the value; true when it's new or changed, so exactly one caller acts on it. */
  boolean advance(long installationId, String repository, String ref, String sha) {
    OffsetDateTime now = OffsetDateTime.now();
    if (jdbc.update("UPDATE github_poll_heads SET sha = ?, updated_at = ? WHERE installation_id = ? AND repository = ? AND ref = ? AND sha <> ?",
        sha, now, installationId, repository, ref, sha) > 0) {
      return true;
    }
    Integer existing = jdbc.queryForObject("SELECT count(*) FROM github_poll_heads WHERE installation_id = ? AND repository = ? AND ref = ?",
        Integer.class, installationId, repository, ref);
    if (existing != null && existing > 0) {
      return false;
    }
    try {
      jdbc.update("INSERT INTO github_poll_heads (installation_id, repository, ref, sha, updated_at) VALUES (?, ?, ?, ?, ?)",
          installationId, repository, ref, sha, now);
      return true;
    } catch (DuplicateKeyException e) {
      return false; // another instance got there first
    }
  }

  void forget(long installationId, String repository, String ref) {
    jdbc.update("DELETE FROM github_poll_heads WHERE installation_id = ? AND repository = ? AND ref = ?", installationId, repository, ref);
  }

  /** Drops the pull requests that are no longer open (or are drafts again). */
  void keepPullRequests(long installationId, String repository, Set<String> open) {
    jdbc.query("SELECT ref FROM github_poll_heads WHERE installation_id = ? AND repository = ? AND ref LIKE 'pr:%'",
            (rs, i) -> rs.getString(1), installationId, repository)
        .stream().filter(ref -> !open.contains(ref)).forEach(ref -> forget(installationId, repository, ref));
  }

  /** Drops repositories the installation no longer covers. */
  void keepRepositories(long installationId, Set<String> repositories) {
    jdbc.query("SELECT DISTINCT repository FROM github_poll_heads WHERE installation_id = ?", (rs, i) -> rs.getString(1), installationId)
        .stream().filter(r -> !repositories.contains(r))
        .forEach(r -> jdbc.update("DELETE FROM github_poll_heads WHERE installation_id = ? AND repository = ?", installationId, r));
  }
}
