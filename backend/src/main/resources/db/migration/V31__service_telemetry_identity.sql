-- Services can now register themselves from their telemetry. A service is identified by its name
-- within an environment, so "checkout" in prod and "checkout" in staging are separate services.
ALTER TABLE services ADD COLUMN environment VARCHAR(64);
ALTER TABLE services ADD COLUMN language VARCHAR(32);
ALTER TABLE services ADD COLUMN version VARCHAR(64);
ALTER TABLE services ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE services ADD COLUMN last_telemetry_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_services_org_name_environment
    ON services(organization_id, name, COALESCE(environment, ''));
