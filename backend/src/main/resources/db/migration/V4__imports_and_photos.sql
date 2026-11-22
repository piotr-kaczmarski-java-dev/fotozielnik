-- Import batches and photos (FR-004..FR-009). Every library table has owner_id; queries always filter by it.

CREATE TABLE import_batch (
    id              uuid         PRIMARY KEY,
    owner_id        uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    label           varchar(200) NOT NULL,                  -- usually the chosen folder's name
    status          varchar(20)  NOT NULL CHECK (status IN ('UPLOADING', 'FINISHED')),
    files_found     int          NOT NULL DEFAULT 0,        -- JPEGs the browser found
    files_skipped   int          NOT NULL DEFAULT 0,        -- non-JPEG files, reported by the browser
    files_duplicate int          NOT NULL DEFAULT 0,        -- already in the library (hash check)
    files_uploaded  int          NOT NULL DEFAULT 0,        -- new photos stored by this batch
    files_failed    int          NOT NULL DEFAULT 0,        -- rejected uploads (corrupt, not a JPEG...)
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    version         bigint       NOT NULL DEFAULT 0
);
CREATE INDEX import_batch_owner_created_idx ON import_batch (owner_id, created_at DESC);

CREATE TABLE photo (
    id                uuid          PRIMARY KEY,
    owner_id          uuid          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    import_batch_id   uuid          REFERENCES import_batch (id) ON DELETE SET NULL,
    sha256            varchar(64)   NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    size_bytes        bigint        NOT NULL,
    storage_key       varchar(200)  NOT NULL,
    original_path     varchar(1000) NOT NULL,                -- path inside the imported folder
    file_modified_at  timestamptz,                           -- as reported by the browser
    -- EXIF (FR-007), filled by background processing
    taken_at_local    timestamp,                             -- wall-clock time from the camera
    taken_at          timestamptz,                           -- only when EXIF has an offset (or GPS time)
    camera_make       varchar(100),
    camera_model      varchar(100),
    camera_serial     varchar(100),
    lens              varchar(200),
    width             int,
    height            int,
    orientation       smallint,                              -- EXIF orientation 1..8
    location          geography(Point, 4326),
    location_source   varchar(10)   CHECK (location_source IN ('EXIF', 'INFERRED', 'MANUAL')),
    -- Grid order: taken_at, else taken_at_local read as UTC, else file time, else upload time.
    sort_time         timestamptz   NOT NULL,
    processing_state  varchar(20)   NOT NULL CHECK (processing_state IN ('PENDING', 'DONE', 'FAILED')),
    processing_error  varchar(1000),
    has_derivatives   boolean       NOT NULL DEFAULT false,  -- thumbnail + preview stored
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    version           bigint        NOT NULL DEFAULT 0,
    CONSTRAINT photo_owner_sha256_uq UNIQUE (owner_id, sha256)
);
CREATE INDEX photo_owner_sort_idx ON photo (owner_id, sort_time DESC, id DESC);
CREATE INDEX photo_batch_state_idx ON photo (import_batch_id, processing_state);
