-- Postgres-backed job queue (ADR-0009). Claimed with FOR UPDATE SKIP LOCKED; a lease (locked_until) lets
-- another worker pick up the job if the one running it died.
CREATE TABLE job (
    id           uuid          PRIMARY KEY,
    owner_id     uuid          REFERENCES app_user (id) ON DELETE CASCADE,  -- null for system jobs
    type         varchar(50)   NOT NULL,
    payload      jsonb         NOT NULL DEFAULT '{}',
    status       varchar(20)   NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    priority     int           NOT NULL DEFAULT 0,                          -- higher runs first
    attempts     int           NOT NULL DEFAULT 0,
    max_attempts int           NOT NULL DEFAULT 5,
    run_after    timestamptz   NOT NULL,                                    -- retry backoff
    locked_until timestamptz,
    locked_by    varchar(100),
    last_error   varchar(2000),
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL,
    finished_at  timestamptz
);
CREATE INDEX job_queued_idx ON job (priority DESC, run_after) WHERE status = 'QUEUED';
CREATE INDEX job_running_lease_idx ON job (locked_until) WHERE status = 'RUNNING';
CREATE INDEX job_finished_idx ON job (finished_at) WHERE status IN ('DONE', 'FAILED');
