-- Import progress counts photos per (owner, batch, state). With the old (import_batch_id, processing_state) index
-- Postgres also scanned the owner's whole sort index and read every photo row: 46 ms for a 10k-photo batch in a
-- 300k library (tools/perf). This index answers the count from the index alone: ~2 ms.
CREATE INDEX photo_owner_batch_state_idx ON photo (owner_id, import_batch_id, processing_state);
DROP INDEX photo_batch_state_idx;
