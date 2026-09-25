-- Alert rules can open an incident with a given severity instead of (or as well as) posting a
-- webhook, so the webhook becomes optional.
ALTER TABLE alert_rules ADD COLUMN incident_severity VARCHAR(20);
ALTER TABLE alert_rules ALTER COLUMN webhook_url DROP NOT NULL;

-- Incidents opened by an alert have no human reporter, and remember which service's alert opened
-- them so repeat alerts update the same incident and recovery can resolve it.
ALTER TABLE incidents ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE incidents
    ADD COLUMN triggered_by_service_id UUID REFERENCES services(id) ON DELETE SET NULL;
CREATE INDEX idx_incidents_triggered_by_service
    ON incidents(triggered_by_service_id) WHERE status <> 'RESOLVED';
