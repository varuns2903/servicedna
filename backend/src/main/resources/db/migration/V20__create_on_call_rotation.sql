CREATE TABLE on_call_rotations (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
  rotation_length_days INTEGER NOT NULL DEFAULT 7,
  start_date DATE NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE on_call_rotation_members (
  id UUID PRIMARY KEY,
  rotation_id UUID NOT NULL REFERENCES on_call_rotations(id) ON DELETE CASCADE,
  organization_member_id UUID NOT NULL REFERENCES organization_members(id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (rotation_id, position)
);
CREATE INDEX idx_on_call_rotation_members_rotation ON on_call_rotation_members(rotation_id);
