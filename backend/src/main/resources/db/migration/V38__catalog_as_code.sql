-- Metadata a repository's servicedna.yaml declares (traffic can't reveal it), and alert rules
-- that file manages: rules with managed_by = 'CATALOG' are replaced whenever it's scanned.
ALTER TABLE services ADD COLUMN owner VARCHAR(255);
ALTER TABLE services ADD COLUMN tier VARCHAR(16);
ALTER TABLE alert_rules ADD COLUMN managed_by VARCHAR(16);
