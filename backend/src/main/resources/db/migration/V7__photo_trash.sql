-- Trash (FR-022): a deleted photo keeps its files for a retention period (default 30 days), then a background
-- job removes the objects and the row. The grid filters on deleted_at IS NULL.
ALTER TABLE photo ADD COLUMN deleted_at timestamptz;

-- trash page (newest deleted first) and the nightly purge of expired photos
CREATE INDEX photo_trash_idx ON photo (owner_id, deleted_at DESC, id DESC) WHERE deleted_at IS NOT NULL;
CREATE INDEX photo_trash_expiry_idx ON photo (deleted_at) WHERE deleted_at IS NOT NULL;
