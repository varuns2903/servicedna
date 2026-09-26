-- Calls between services (and to databases, external hosts and topics) observed in traces,
-- aggregated per minute at operation granularity. The dependency graph and API flow graph are
-- both derived from this table.
CREATE TABLE observed_calls (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    bucket_start TIMESTAMPTZ NOT NULL,
    source_service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    source_operation VARCHAR(255) NOT NULL,
    -- A registered service, or NULL for a node that isn't one (database, external host, topic).
    target_service_id UUID REFERENCES services(id) ON DELETE CASCADE,
    target_kind VARCHAR(16) NOT NULL,
    target_name VARCHAR(255) NOT NULL,
    target_operation VARCHAR(255) NOT NULL,
    protocol VARCHAR(16) NOT NULL,
    calls BIGINT NOT NULL,
    errors BIGINT NOT NULL,
    duration_sum_ms BIGINT NOT NULL,
    duration_max_ms BIGINT NOT NULL,
    -- Latency histogram: calls at or under each bound (ms); the last bucket is everything slower.
    le_10 BIGINT NOT NULL, le_50 BIGINT NOT NULL, le_100 BIGINT NOT NULL, le_250 BIGINT NOT NULL,
    le_500 BIGINT NOT NULL, le_1000 BIGINT NOT NULL, le_2500 BIGINT NOT NULL, le_5000 BIGINT NOT NULL,
    le_10000 BIGINT NOT NULL, le_inf BIGINT NOT NULL,
    CONSTRAINT uq_observed_calls UNIQUE (organization_id, bucket_start, source_service_id, source_operation,
        target_kind, target_name, target_operation, protocol)
);

CREATE INDEX idx_observed_calls_org_bucket ON observed_calls(organization_id, bucket_start);
