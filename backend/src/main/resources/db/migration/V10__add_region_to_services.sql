ALTER TABLE services ADD COLUMN region VARCHAR(50) NOT NULL DEFAULT 'global';
CREATE INDEX idx_services_region ON services(region);
