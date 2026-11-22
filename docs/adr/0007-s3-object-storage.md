# ADR-0007: Store photos in S3-compatible object storage (Garage), streamed through the app

- Status: Accepted
- Date: 2026-11-18
- Related: FR-004, FR-008, FR-009, NFR-006, NFR-013

## Context
Originals (10-20 GB per user) must stay unchanged and portable to a cloud provider later. Thumbnails and
previews are derived. Access must respect per-user privacy.

## Decision
- A `BlobStore` interface (`storage` package) with one implementation on AWS SDK v2 S3 (works with Garage,
  Hetzner, OVH, AWS, Backblaze). The app uses only basic S3 calls (put/get/head/delete/list).
- Server: **Garage** (single node) in dev and production compose and in `*IT` (Testcontainers), from
  `docker/garage/Dockerfile`, which wraps the official image and bootstraps layout, key and bucket from env vars.
  MinIO was the first choice, but its community edition was archived in 2026-04 and its images withdrawn.
- One bucket; keys `u/{ownerId}/o/{sha256}` (original), `u/{ownerId}/t/{sha256}.webp` (thumb),
  `u/{ownerId}/p/{sha256}.webp` (preview). Dedup per user, never across users.
- Images are served through the app (authorisation check, `Cache-Control: private, max-age=...`,
  `ETag` = hash), not via public or presigned URLs, so the storage server is never exposed to the internet.

## Alternatives considered
| Option | Pros | Cons |
|---|---|---|
| Local filesystem | Simplest | Not portable to cloud; harder to scale; backups ad hoc |
| Presigned URLs | Offloads bytes from the app | Storage server must be public; URLs leak access for their lifetime |
| Cross-user dedup | Saves space | Privacy and deletion complexity |
| MinIO | Was the de-facto choice | Community edition archived (2026-04), Docker images withdrawn |
| RustFS | MinIO drop-in, Apache-2.0 | 1.0 only since 2026-10 - young |
| SeaweedFS | Mature, Apache-2.0 | More configuration; some features flagged not production-ready |

## Consequences
- New dependency: AWS SDK v2 S3 (M1, task 3). Garage added to `compose.yml` with a fixed local port 3900
  (Spring Boot has no compose service connection for S3). Garage is AGPL; we run it unmodified as a separate
  container, which places no obligations on our code. Moving to another S3 server = copy the bucket.
- The app streams image bytes; thumbnails are small, so this is fine at expected load.

## Amendment 2026-11-19
Derivative keys have **no file extension**: `u/{ownerId}/t/{sha256}` and `u/{ownerId}/p/{sha256}`. The format is
the object's `Content-Type` (JPEG from the ImageIO fallback today, possibly WebP once libvips is in, see ADR-0013),
so changing the generator does not change keys. Off-site backup uses `rclone` instead of `mc` (MinIO's client).
