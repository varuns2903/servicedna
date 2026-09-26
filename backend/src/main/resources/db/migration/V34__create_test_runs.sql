-- Test Studio runs: a request sent by a runner inside the target environment, traced end to end.
CREATE TABLE test_runs (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    environment VARCHAR(64),
    protocol VARCHAR(16) NOT NULL,
    target_service_id UUID REFERENCES services(id) ON DELETE SET NULL,
    -- What to call: JSON with the fields the protocol needs (method/path/baseUrl, grpc method, topic).
    target TEXT NOT NULL,
    -- What to send: JSON {headers, body, key}.
    request TEXT NOT NULL,
    trace_id VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    -- The entry call's result from the runner: JSON {status, headers, body, durationMs, ...}.
    result TEXT,
    error TEXT,
    runner VARCHAR(100),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_at TIMESTAMPTZ,
    responded_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_test_runs_queue ON test_runs(status, created_at);
CREATE INDEX idx_test_runs_org ON test_runs(organization_id, created_at);
