# ADR-0008: Use a custom PostgreSQL 18 image with PostGIS and pgvector

- Status: Accepted
- Date: 2026-11-18
- Related: FR-012, FR-016, FR-019

## Context
Locations need spatial types/queries (PostGIS); embeddings need vector similarity search (pgvector).
No official image has both. Compose and Testcontainers must use the same database.

## Decision
`docker/postgres/Dockerfile`: `FROM postgis/postgis:18-...` + `postgresql-18-pgvector` package. Built by compose
(`build:`) and by Testcontainers (`ImageFromDockerfile` or a locally tagged image). Extensions
(`postgis`, `vector`, `pg_trgm`) are created by the first Flyway migration.

## Alternatives considered
| Option | Pros | Cons |
|---|---|---|
| `pgvector/pgvector` + no PostGIS | Official image | Hand-rolled geo math; no spatial index |
| Separate vector DB (Qdrant) | Purpose-built | Extra service, breaks "one compose file", data split |
| Third-party combined image | No Dockerfile | Unclear maintenance/supply chain |

## Consequences
- `compose.yml` and `TestcontainersConfiguration` change; CI builds the image (cache it).
- Managed cloud Postgres later must support both extensions (most do).
