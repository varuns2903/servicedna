-- Traces attached to an incident, with a snapshot of their hops so the post-mortem keeps the
-- failure path after the trace store's retention has removed the trace itself.
CREATE TABLE incident_traces (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    trace_id VARCHAR(32) NOT NULL,
    note VARCHAR(1000),
    summary VARCHAR(500) NOT NULL,
    hops TEXT NOT NULL,
    attached_by UUID REFERENCES users(id) ON DELETE SET NULL,
    attached_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_incident_traces UNIQUE (incident_id, trace_id)
);
CREATE INDEX idx_incident_traces_incident ON incident_traces(incident_id);
