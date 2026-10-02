-- What the GitHub App last saw of each repository when polling (for a ServiceDNA GitHub can't
-- send webhooks to): the last push, the servicedna.yaml it applied and each open pull request's
-- head, so it acts only on what changed.
CREATE TABLE github_poll_heads (
    installation_id BIGINT NOT NULL REFERENCES github_installations(installation_id) ON DELETE CASCADE,
    repository VARCHAR(255) NOT NULL,
    -- "pushed", "manifest" or "pr:<number>"
    ref VARCHAR(64) NOT NULL,
    sha VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (installation_id, repository, ref)
);
