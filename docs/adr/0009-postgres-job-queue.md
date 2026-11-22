# ADR-0009: Run background work on a Postgres-backed job queue inside the app

- Status: Accepted
- Date: 2026-11-18
- Related: FR-008, FR-010, FR-014, NFR-003, NFR-004

## Context
Imports of tens of thousands of photos need EXIF, thumbnails, embeddings, grouping and identification,
possibly overnight. Work must survive restarts, retry failures and show progress. No extra infrastructure.

## Decision
A `job` table claimed with `SELECT ... FOR UPDATE SKIP LOCKED`; workers in the app on a bounded pool
(CPU-heavy jobs limited to cores - 1). Fields: type, payload jsonb, status, priority, attempts,
`run_after` (backoff), `locked_until` (lease, recovers jobs of crashed workers), last_error, progress.
Handlers are idempotent. Batch-level follow-up jobs (grouping) are enqueued when the last photo job of a
batch finishes. Interactive work has priority over bulk work.

## Alternatives considered
| Option | Pros | Cons |
|---|---|---|
| JobRunr | Dashboard, retries built in | Own tables/conventions; pro features paid; less control over priorities |
| db-scheduler | Small, Postgres-based | Aimed at scheduled tasks, not high-volume queues |
| RabbitMQ / Redis | Mature queues | Extra service, breaks "one compose file" |

## Consequences
- A few hundred lines to own and test (`JobQueueIT` for concurrency, lease expiry, retries).
- Revisit if job volume or features outgrow it (e.g. move to JobRunr).
