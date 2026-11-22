# ADR-0006: Deploy as one Docker Compose stack on an EU VPS, frontend inside the Spring jar

- Status: Accepted
- Date: 2026-11-18
- Related: NFR-004, NFR-006, NFR-010, NFR-013

## Context
Hosting must be trivial (owner requirement) and data must stay in the EU. ML runs in-process and needs
~4 vCPU / 8-16 GB RAM; photos need ~20 GB per user. A later hosted product must be possible.

## Decision
- One EU VPS (e.g. Hetzner/OVH) with a block volume; `deploy/compose.yml` with Caddy (automatic HTTPS),
  the app, Postgres (ADR-0008) and Garage (ADR-0007). The root `compose.yml` stays dev-only.
- One app image: multi-stage build copies the Vite build into the Spring jar's static resources;
  Spring serves `index.html` for client routes.
- Flyway migrates on start. Secrets in a server-side `.env`.
- Backups: nightly `pg_dump` and bucket mirror to off-site storage, 14 days; restore tested before M1 ends.

## Alternatives considered
| Option | Pros | Cons |
|---|---|---|
| Home server / NAS | No monthly cost | Remote access, hardware and backup burden on the owner |
| PaaS (Fly.io, Render) | Less ops | Expensive for 200 GB + 16 GB RAM; object storage separate anyway |
| Frontend on a CDN | Faster static delivery | Second deployable, CORS/cookies across origins |

## Consequences
- Need a `Dockerfile` for the app, `deploy/compose.yml`, `.env.example`, a backup script and a short runbook.
- Moving to a cloud later = point S3 settings at the provider and use a managed Postgres with the same extensions.
