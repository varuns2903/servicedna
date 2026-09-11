ALTER TABLE incidents ADD COLUMN acknowledged_at TIMESTAMPTZ;
ALTER TABLE incidents ADD COLUMN escalated_at TIMESTAMPTZ;

CREATE TABLE escalation_policies (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
  escalation_email VARCHAR(255) NOT NULL,
  escalate_after_minutes INTEGER NOT NULL DEFAULT 15,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
