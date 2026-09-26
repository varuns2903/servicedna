-- Threshold conditions (LATENCY_ABOVE, ERROR_RATE_ABOVE, CONSECUTIVE_FAILURES) are evaluated on a
-- schedule rather than on status changes, so each rule remembers whether it's currently breached
-- to fire once when the threshold is crossed and once when it clears.
ALTER TABLE alert_rules ADD COLUMN threshold DOUBLE PRECISION;
ALTER TABLE alert_rules ADD COLUMN window_minutes INTEGER;
ALTER TABLE alert_rules ADD COLUMN breached BOOLEAN NOT NULL DEFAULT FALSE;
