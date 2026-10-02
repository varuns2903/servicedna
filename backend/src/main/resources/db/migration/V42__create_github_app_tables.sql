-- GitHub App installations linked to organizations, and the check runs the App reports on pull
-- requests (with the suites whose results it's waiting for).
CREATE TABLE github_installations (
    installation_id BIGINT PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    account_login VARCHAR(255) NOT NULL,
    -- The ServiceDNA user who connected it: syncs and PR checks act with their permissions.
    installed_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    installed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_synced_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_github_installations_org ON github_installations(organization_id);

CREATE TABLE github_check_runs (
    id UUID PRIMARY KEY,
    installation_id BIGINT NOT NULL REFERENCES github_installations(installation_id) ON DELETE CASCADE,
    repository VARCHAR(255) NOT NULL,
    check_run_id BIGINT NOT NULL,
    suite_ids TEXT NOT NULL,
    summary TEXT,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
