-- Per-environment settings. Test runs send real requests, so production-like environments
-- (prod, production, live) need them switched on explicitly.
CREATE TABLE environment_settings (
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    environment VARCHAR(64) NOT NULL,
    allow_test_runs BOOLEAN NOT NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, environment)
);
