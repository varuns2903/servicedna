-- Requests each service handled, per minute, from its server and consumer spans: counts, errors and
-- a latency histogram (bucket upper bounds in RequestStats.BOUNDS_MS; the last is unbounded), so
-- alert rules can judge real traffic. Rows are only ever incremented, so instances add safely.
CREATE TABLE service_request_stats (
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    bucket_start TIMESTAMP WITH TIME ZONE NOT NULL,
    requests BIGINT NOT NULL,
    errors BIGINT NOT NULL,
    duration_max_ms BIGINT NOT NULL,
    le_10 BIGINT NOT NULL, le_25 BIGINT NOT NULL, le_50 BIGINT NOT NULL, le_100 BIGINT NOT NULL,
    le_250 BIGINT NOT NULL, le_500 BIGINT NOT NULL, le_1000 BIGINT NOT NULL, le_2500 BIGINT NOT NULL,
    le_5000 BIGINT NOT NULL, le_10000 BIGINT NOT NULL, le_inf BIGINT NOT NULL,
    PRIMARY KEY (service_id, bucket_start)
);
CREATE INDEX idx_service_request_stats_bucket ON service_request_stats(bucket_start);
