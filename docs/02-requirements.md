# Requirements - fotozielnik.pl

Status: DRAFT

IDs are permanent. Status values: `Proposed`, `Planned`, `In progress`, `Done`, `Dropped`.
Acceptance criteria must be checkable - ideally by an automated test (name the test).
Milestones: M1 accounts + import, M2 observations, M3 identification, M4 browsing (= MVP), see 05-roadmap.

## Functional requirements

| ID | Requirement | Priority | Milestone | Status | Acceptance criteria / test |
|---|---|---|---|---|---|
| FR-001 | A user signs in with email + password and signs out | Must | M1 | Done | Session cookie; wrong password -> clear Polish message; `AuthIT`, `e2e/login.spec.ts` |
| FR-002 | An admin creates user accounts and resets passwords; no self-registration | Must | M1 | Done | Non-admin gets 403; `AdminUsersIT`; forced change of the temporary password in `e2e/login.spec.ts`; admin screen `/admin/users` |
| FR-003 | The first admin is created from environment variables on first start | Must | M1 | Done | Empty DB + `ADMIN_EMAIL`/`ADMIN_INITIAL_PASSWORD` -> admin exists; `BootstrapAdminIT` |
| FR-004 | A user uploads a whole folder tree (folder picker or drag and drop); only JPEG files are taken | Must | M1 | Done | Non-JPEG files listed as skipped; `ImportDedupIT`, `runImport.test.ts`, `e2e/import.spec.ts` |
| FR-005 | Duplicates (same SHA-256 within the user's library) are skipped before upload; re-importing is safe | Must | M1 | Done | Second import of same folder uploads 0 files; `ImportDedupIT` |
| FR-006 | An interrupted import resumes by choosing the same folder again | Must | M1 | Done | Already-stored files are skipped; `ImportDedupIT`, `e2e/import.spec.ts` (same folder again uploads nothing) |
| FR-007 | EXIF is read: capture time (+ offset if present), GPS, camera make/model/serial, lens, orientation | Must | M1 | Done | Fixture images; `ExifReaderTest` (EXIF built in the test by `ExifJpeg`), `DerivativesIT` |
| FR-008 | Thumbnails and previews are generated in the background; import progress is visible | Must | M1 | Done | `DerivativesIT`; progress in `GET /api/imports/{id}` shown live on the import page; `e2e/import.spec.ts` |
| FR-009 | A user browses their photos in a date-ordered grid and opens a large preview | Must | M1 | Done | Paginated (keyset cursor, infinite grid by month); only own photos (404 for others); `PhotoAccessIT`, `e2e/import.spec.ts` |
| FR-010 | Photos are grouped automatically into observations by time proximity and image similarity | Must | M2 | Proposed | Fixture sequence -> expected groups; `ObservationGrouperTest` |
| FR-011 | A user splits and merges observations; automatic regrouping never changes user-edited ones | Must | M2 | Proposed | `ObservationEditIT` |
| FR-012 | Missing GPS is filled from the user's photos taken close in time, marked as inferred | Should | M2 | Proposed | `GpsInferenceTest` |
| FR-013 | A user sets a clock offset per camera, which corrects capture times for grouping and GPS fill | Should | M2 | Proposed | `CameraClockIT` |
| FR-014 | Each observation gets ranked species suggestions from a local model limited to the Central European vascular-plant list | Must | M3 | Proposed | `SpeciesClassifierIT` with fixture images |
| FR-015 | Confidence tiers: auto-accept / review queue / unknown, thresholds configurable | Must | M3 | Proposed | `IdentificationPolicyTest` |
| FR-016 | Confirmed observations become references for nearest-neighbour matching of the user's other photos | Must | M3 | Proposed | `KnnMatcherIT` |
| FR-017 | Review UI: grid with multi-select, bulk "set species", autocomplete for Latin and Polish names, keyboard shortcuts | Must | M3 | Proposed | `e2e/review.spec.ts` |
| FR-018 | Browse by taxonomy tree (family > genus > species) with counts | Must | M4 | Proposed | `e2e/taxonomy.spec.ts` |
| FR-019 | Browse observations on a map (clustered) and by date | Must | M4 | Proposed | `e2e/map.spec.ts` |
| FR-020 | Taxonomy backbone from GBIF (vascular plants, Central Europe) with Polish vernacular names | Must | M3 | Proposed | Loader test on a sample file |
| FR-021 | Taxa carry a sensitivity level (protected / red-listed); used to blur locations when sharing exists | Could | Later | Proposed | Owner always sees exact coordinates |

## Non-functional requirements

| ID | Category | Requirement | Target / budget | Status |
|---|---|---|---|---|
| NFR-001 | Performance | API latency | p95 < 500 ms for interactive reads at expected load; grid pages paginated | Proposed |
| NFR-002 | Performance | Frontend load | First screen < 3 s on desktop broadband; thumbnails lazy-loaded | Proposed |
| NFR-003 | Capacity | Users / data | 10 users, 3 concurrent, 300k photos, 200 GB originals; one 30k-photo import processed overnight (< 8 h) on 4 vCPU | Proposed |
| NFR-004 | Availability | Uptime, maintenance | Best effort (~99 %); restart-safe jobs; downtime for deploys acceptable | Proposed |
| NFR-005 | Security | Authentication, authorization | Deny-by-default; every query scoped to the owner; passwords hashed (bcrypt/argon2); CSRF protection with cookies; HTTPS only in prod | In progress |
| NFR-006 | Privacy | Personal data | GDPR: data in the EU; deleting a user deletes their photos, objects and rows (`UserDeletionIT`, `e2e/admin.spec.ts`); no third-party services receive photos | In progress |
| NFR-007 | Compatibility | Browsers, devices | Last 2 versions of Chrome/Firefox/Edge/Safari; desktop >= 1024 px designed, >= 360 px usable | Proposed |
| NFR-008 | Accessibility | Standard | WCAG 2.2 AA; base font >= 18 px; click targets >= 44 px; keyboard operable | Proposed |
| NFR-009 | Localization | Languages | Polish at launch; all UI text in i18n files; species as Latin + Polish name | Proposed |
| NFR-010 | Operability | Logs, metrics, backups | Structured logs, `/actuator/health`; nightly DB dump + object sync off-site, kept 14 days; restore tested once before MVP | Proposed |
| NFR-011 | Quality | Automated tests | Unit/slice tests for all logic; *IT per feature; e2e per critical flow | Planned |
| NFR-012 | Legal | Licences, policies | Model weights, GBIF data (CC BY 4.0, attribution) and libraries compatible with later commercial use | Proposed |
| NFR-013 | Portability | Deployment | One compose file; storage via S3 API only; no Python or external AI service at runtime | Proposed |

## Constraints
- Team / time: one developer with AI agents, ~5-10 h/week, no hard deadline.
- Budget: ~EUR 20-40/month hosting (assumed).
- Tech: Java 25, Spring Boot 4, PostgreSQL (+ PostGIS, pgvector), React + TypeScript (ADR-0001);
  S3 API (Garage), ONNX Runtime in-process.
- Other: first users are older and non-technical - UI simplicity outranks features.

## Assumptions and risks
| # | Assumption or risk | Impact | Mitigation |
|---|---|---|---|
| R1 | BioCLIP accuracy on Central European flora too low to be useful | High -> Low/Medium | Spike done on iNaturalist photos: BioCLIP 2 over 2533 Polish species, top-3 0.98 (common species, best photos) / 0.94 (1153 ordinary photos incl. rare species), same via Java/ONNX int8 (ADR-0010 "Spike results"). Still to do: re-run on ~200 of the father's identified photos |
| R2 | In-JVM image decoding/ML too slow or memory-hungry for overnight imports | Low | Measured: ~0.4 s per 24 MP JPEG for thumbnails (ADR-0013), 0.36 s per photo for BioCLIP 2 int8 in Java on one core (ADR-0010) -> ~4 h for 30k photos on 3 workers. Re-check on the VPS |
| R3 | Browser upload of 20 GB is fragile (tab closed, sleep) | Medium | Per-file resume via hash check; clear progress and "continue" instructions |
| R4 | Polish vernacular names incomplete in GBIF | Medium | Measure coverage; merge a Polish checklist if licence allows. iNaturalist has Polish names for 100% of the 1000 most common species, 91% of all 2533 |
| R5 | Scope creep vs 5-10 h/week | High | Milestones usable on their own; non-goals in 01-vision |

## Change history
| Date | Change |
|---|---|
| 2026-11-19 | NFR-006: admin deletes a user with the whole library |
| 2026-11-19 | FR-001..FR-009 done (API + UI + e2e); M1 left: VPS deploy, real archive, restore test |
| 2026-11-18 | First draft from kickoff |
