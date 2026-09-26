-- Service-to-service calls ever seen, so a dependency appearing for the first time can be alerted
-- on. Backfilled from recorded traffic so existing edges don't all count as new.
CREATE TABLE seen_service_edges (
    source_service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    target_service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (source_service_id, target_service_id)
);
INSERT INTO seen_service_edges (source_service_id, target_service_id, organization_id, first_seen_at)
SELECT source_service_id, target_service_id, MIN(organization_id::text)::uuid, MIN(bucket_start)
FROM observed_calls
WHERE target_service_id IS NOT NULL AND source_service_id <> target_service_id
GROUP BY source_service_id, target_service_id;
