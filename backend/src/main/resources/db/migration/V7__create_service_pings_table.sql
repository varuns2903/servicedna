CREATE TABLE service_pings (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    latency_ms INTEGER,
    message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_service_pings_service ON service_pings(service_id);
CREATE INDEX idx_service_pings_created_at ON service_pings(created_at DESC);
