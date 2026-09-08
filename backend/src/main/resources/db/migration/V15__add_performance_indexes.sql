-- Indexes for heavy read queries and dashboard aggregations

CREATE INDEX IF NOT EXISTS idx_services_org_status ON services(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_incidents_org_status_sev ON incidents(organization_id, status, severity);
CREATE INDEX IF NOT EXISTS idx_service_pings_service_created ON service_pings(service_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

