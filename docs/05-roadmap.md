# Roadmap - fotozielnik.pl

Status: DRAFT

Each milestone has **exit criteria**. Do not start the next milestone's features before they are met.
Dates are rough targets at ~5-10 h/week (assumed). Every milestone from M1 on is deployed and usable, so the
first users can start importing early and their real data tunes M2/M3.

| ID | Milestone | Goal | Exit criteria | Target date | Status |
|---|---|---|---|---|---|
| M0 | Foundations | Repo, tooling, docs skeleton | Kickoff done, docs at DRAFT, ADRs 0005-0013 accepted | 2026-11 | Done |
| M1 | Accounts + import | Users can upload their whole archive and see it | FR-001..FR-009 done; deployed on the VPS with HTTPS; one real archive (~20 GB) imported; e2e green; backup restored once | 2027-02 | Planned |
| M2 | Observations | Photos grouped into observations | FR-010..FR-013; grouping tuned on a real archive | 2027-04 | Planned |
| M3 | Identification | Auto-ID + review | FR-014..FR-017, FR-020; top-3 accuracy measured | 2027-07 | Planned |
| M4 | Browsing = MVP | Taxonomy tree + map | FR-018, FR-019; NFR targets checked; father's group using it unaided | 2027-09 | Planned |

## Now (M1)
- ~~Accept ADRs 0005-0013~~ (done 2026-11-18).
- ~~Custom Postgres image (PostGIS + pgvector) for compose and Testcontainers~~ (done, V2 migration).
- ~~S3 server in dev compose~~ (done: Garage, `docker/garage`; also in Testcontainers).
- ~~Backend walking skeleton~~ (done 2026-11-19): admin bootstrap, session login + CSRF, admin user API,
  BlobStore on Garage, import batches with hash check/upload/dedup/resume, photo grid API with keyset paging,
  image streaming, Postgres job queue, EXIF + thumbnails/previews in the background. 73 backend tests.
- ~~Production compose (Caddy + app + Postgres + Garage); backup script~~ (done 2026-11-19, `deploy/`; tested
  locally incl. a dump/restore round trip - not yet on the VPS).
- ~~Frontend~~ (done 2026-11-19): login + forced password change, library grid, photo page, import page
  (folder picker/drop, hashing workers, parallel upload with retries, live progress), admin users, account;
  e2e `login.spec.ts`, `import.spec.ts` on an isolated stack.
- ~~Deploy to the VPS with HTTPS~~ (done 2026-11-22: Hetzner Cloud, Helsinki, lean start 2 vCPU / 4 GB +
  50 GB volume, temporary sslip.io hostname until the domain is bought).
- Import one real archive (~20 GB); benchmark processing on the small server (R2).
- Off-site backups (`backup.sh` + rclone remote, e.g. B2 EU or own PC) and one restore: postponed while the
  server only holds copies of the father's originals (Hetzner server backups cover DB + `.env`, not the
  volume). **Must be done before users enter data that exists only on the server (M2 edits, M3 confirmations).**
- ~~Time-boxed ML spike (R1)~~ (done 2026-11-19 on iNaturalist photos: BioCLIP 2 int8 in Java, top-3 0.98;
  ADR-0010). ~~Re-run on the father's identified photos~~ (done 2026-11-22: 416 photos from Facebook, top-1 0.82,
  top-3 0.92; ADR-0010).

## Next
- M2: embeddings job, grouping, split/merge, camera clock offsets, GPS fill.
- M3: GBIF taxonomy loader, prototypes, classifier + kNN, review UI.

## Later
- Import option "make photos smaller before sending" (recompress/downscale in the browser) - see open design
  questions in 03-product-design; decide after asking the father.
- M4 browsing; then phenology views, life lists, iNaturalist/GBIF export, sharing in groups
  (with sensitive-location blurring, FR-021), RAW support if requested, hosted/commercial version.

## Change history
| Date | Change |
|---|---|
| 2026-11-22 | Deployed on Hetzner (lean 4 GB server while there is one user); next: backups, real archive |
| 2026-11-19 | ML spike: BioCLIP 2 int8 via ONNX in Java chosen (ADR-0010) |
| 2026-11-19 | M1 backend, frontend and deploy files done; next: VPS + real archive |
| 2026-11-18 | ADRs accepted; Postgres image done; M0 done |
| 2026-11-18 | First draft from kickoff; MVP split into M1-M4 for a 5-10 h/week pace |
