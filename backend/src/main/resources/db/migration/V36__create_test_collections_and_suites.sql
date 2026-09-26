-- Saved test cases (collections) and runs of a set of cases (suites) with assertions.
CREATE TABLE test_collections (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    -- JSON array of {name, request, assertions}.
    cases TEXT NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_test_collections_name UNIQUE (organization_id, name)
);

CREATE TABLE test_suites (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    collection_id UUID REFERENCES test_collections(id) ON DELETE SET NULL,
    environment VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_test_suites_org ON test_suites(organization_id, created_at);

ALTER TABLE test_runs ADD COLUMN suite_id UUID REFERENCES test_suites(id) ON DELETE CASCADE;
ALTER TABLE test_runs ADD COLUMN case_name VARCHAR(255);
ALTER TABLE test_runs ADD COLUMN assertions TEXT;
ALTER TABLE test_runs ADD COLUMN assertion_results TEXT;
ALTER TABLE test_runs ADD COLUMN passed BOOLEAN;
