-- Fills a THROWAWAY database with a large library for load tests (tools/perf/README.md).
-- psql -v owner=<uuid> -v photos=300000 -v batches=30 -f seed.sql
-- Never run this against a database with real data.
\set ON_ERROR_STOP on
BEGIN;
INSERT INTO import_batch (id, owner_id, label, status, files_found, files_uploaded, created_at, updated_at)
SELECT gen_random_uuid(), :'owner', 'Seed ' || b, 'FINISHED', :photos / :batches, :photos / :batches,
       now() - (b || ' days')::interval, now()
FROM generate_series(1, :batches) b;

WITH b AS (SELECT id, row_number() OVER (ORDER BY created_at) - 1 AS n FROM import_batch WHERE owner_id = :'owner')
INSERT INTO photo (id, owner_id, import_batch_id, sha256, size_bytes, storage_key, original_path,
                   taken_at_local, camera_make, camera_model, width, height, orientation, location, location_source,
                   sort_time, processing_state, has_derivatives, created_at, updated_at)
SELECT gen_random_uuid(), :'owner', b.id,
       encode(sha256((:'owner' || i)::bytea), 'hex'), 8000000 + i % 4000000,
       'u/' || :'owner' || '/o/' || encode(sha256((:'owner' || i)::bytea), 'hex'),
       'Seed/' || (i / 500) || '/IMG_' || i || '.jpg',
       t.local, 'NIKON CORPORATION', 'NIKON D7500', 6000, 4000, 1,
       CASE WHEN i % 10 < 7 THEN ST_SetSRID(ST_MakePoint(14 + random() * 10, 49 + random() * 5), 4326)::geography END,
       CASE WHEN i % 10 < 7 THEN 'EXIF' END,
       t.local AT TIME ZONE 'UTC', 'DONE', true, now(), now()
FROM generate_series(1, :photos) i
JOIN b ON b.n = i % :batches
CROSS JOIN LATERAL (SELECT timestamp '2011-01-01' + (i::float / :photos) * interval '15 years'
                            + (random() * interval '2 hours') AS local) t;
COMMIT;
ANALYZE photo;
ANALYZE import_batch;
