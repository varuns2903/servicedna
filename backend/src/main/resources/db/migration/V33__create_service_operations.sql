-- A service's API catalog from its own specs (OpenAPI, .proto, AsyncAPI), sent by `sdna scan`.
-- Traffic-observed operations come from observed_calls; this table holds what the specs declare,
-- including request schemas the Test Studio builds payload templates from.
CREATE TABLE service_operations (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    protocol VARCHAR(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    source VARCHAR(16) NOT NULL,
    description TEXT,
    request_schema TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_service_operations UNIQUE (service_id, protocol, name)
);
