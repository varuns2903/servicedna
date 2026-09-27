-- Optimistic locking for observed_calls: several backend instances can flush counts into the same
-- minute bucket; a version check makes a concurrent update retry instead of overwriting.
ALTER TABLE observed_calls ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
