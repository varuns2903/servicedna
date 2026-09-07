CREATE TABLE services (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    repository_url VARCHAR(512),
    status VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN',
    api_key VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (organization_id, name)
);

CREATE TABLE service_dependencies (
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    depends_on_service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    PRIMARY KEY (service_id, depends_on_service_id)
);

CREATE INDEX idx_services_org ON services(organization_id);
CREATE INDEX idx_services_api_key ON services(api_key);
