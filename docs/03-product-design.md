# Product design - fotozielnik.pl

Status: DRAFT

## Roles and permissions
| Capability | Anonymous | User | Admin |
|---|---|---|---|
| See login page, sign in | yes | - | - |
| Import, browse, edit **own** library | - | yes | yes (own library only) |
| See another user's photos/observations | - | no | **no** (admins manage accounts, not content) |
| Create users, reset passwords, deactivate and delete users | - | - | yes |

Every library query is scoped to the signed-in user (`owner_id`); another user's ID returns 404.

## Use cases

### UC-01 Import a folder (FR-004..FR-008)
- Actor: User
- Main flow: "Dodaj zdjęcia" -> choose a folder (or drop it) -> the app lists JPEGs found and skipped ->
  hashes files in the browser -> asks the server which hashes are new -> uploads only those, a few in
  parallel -> shows progress ("1 240 z 8 300 przesłano; 512 już było w bibliotece") -> processing
  (EXIF, thumbnails, later embeddings) continues on the server, also after the tab is closed.
- Alternatives / errors: tab closed or network lost -> choose the same folder again, stored files are skipped;
  unreadable/corrupt JPEG -> counted as failed with file name; storage full -> clear error.
- Business rules: JPEG only; dedup by SHA-256 per user; originals stored unchanged; folder path kept.

### UC-02 Review automatic grouping (FR-010..FR-013)
- Actor: User
- Main flow: after processing, photos appear as observations (cover photo, date, place, count) ->
  user opens one -> moves photos out (split) or selects several observations and merges them.
- Business rules: user-edited observations are locked against regrouping. GPS filled from photos within
  a time window is marked "przybliżona lokalizacja".

### UC-03 Identify species (FR-014..FR-017)
- Actor: User
- Main flow: "Do przejrzenia" queue shows observations with top-3 suggestions (Latin + Polish name, photo
  of a confirmed reference if any) -> user accepts with one click/key, picks another suggestion, or types
  a name (autocomplete) -> multi-select + "Ustaw gatunek" for many at once.
- Business rules: high confidence -> auto-accepted (still editable); medium -> review queue; low -> "nieznane".
  Confirmed IDs become kNN references. User decisions are never overwritten by the model.

### UC-04 Browse (FR-018, FR-019)
- Actor: User
- Main flow: taxonomy tree (rodzina > rodzaj > gatunek with counts) -> species page with observations;
  map with clustered observations; timeline by year/month.

### UC-05 Manage accounts (FR-002, FR-003)
- Actor: Admin
- Main flow: admin creates a user (email, name, temporary password) and hands the password over in person;
  user changes it on first sign-in (assumed). Admin can reset a password or deactivate a user.

## Domain model
| Entity | Key fields | Relations | Notes (invariants, lifecycle) |
|---|---|---|---|
| AppUser | id (uuid), email (unique), password_hash, display_name, locale, role USER/ADMIN, active | owns everything below | Deleting a user deletes the library and stored objects |
| ImportBatch | id, owner, label, status, counts (total/new/duplicate/skipped/failed) | has Photos | One per import session; progress source |
| Photo | id, owner, sha256, size, storage_key, original_path, taken_at_local, taken_at (tz), camera make/model/serial, lens, w/h, orientation, location (geography Point), location_source EXIF/INFERRED/MANUAL, processing_state, observation (nullable) | ImportBatch, Observation, PhotoEmbedding | UNIQUE(owner, sha256). Original never modified |
| PhotoEmbedding | photo, model, embedding vector(N) | Photo, MlModel | One per photo per model version; HNSW index |
| CameraClock | owner, camera (serial or make+model), offset_seconds | AppUser | Applied to taken_at for grouping/GPS |
| Observation | id, owner, started_at, ended_at, location, cover_photo, taxon (nullable), id_status UNKNOWN/NEEDS_REVIEW/AUTO_ACCEPTED/CONFIRMED, id_source MODEL/KNN/USER, confidence, notes, user_edited | Photos (1..n), Taxon, IdSuggestions | One plant at one place and time; user-edited ones are never regrouped |
| IdSuggestion | observation, rank, taxon, score, source MODEL/KNN, model | Observation, Taxon | Replaced on each identification run |
| Taxon | id (GBIF key), rank, scientific_name, authorship, parent, family, genus, accepted (for synonyms), in_scope, sensitivity NONE/OBSCURE/HIDE | TaxonName, TaxonPrototype | Global reference data; vascular plants of Central Europe |
| TaxonName | taxon, lang, name, preferred | Taxon | Polish + Latin; trigram index for autocomplete |
| MlModel | id, name, version, embedding_dim, file_sha256 | | Allows several model versions side by side |
| TaxonPrototype | taxon, model, embedding | Taxon, MlModel | Precomputed BioCLIP text embeddings |
| Job | id, owner, type, payload (jsonb), status, priority, attempts, run_after, locked_until, last_error, progress | | Postgres queue (ADR-0009) |

## Screens and flows
Sign-in -> Library (date grid) ; top navigation with few, large entries: Biblioteka, Dodaj zdjęcia,
Do przejrzenia (M3), Gatunki (M4), Mapa (M4), Konto/Admin. Each list has empty states with one clear next
step ("Nie masz jeszcze zdjęć - Dodaj zdjęcia").

| Screen | Route | Purpose | Use cases |
|---|---|---|---|
| Sign-in | `/login` | Email + password | - |
| Library | `/` | Photos (M1) / observations (M2+) by date, infinite grid | UC-02, UC-04 |
| Photo | `/photos/:id` | Large preview, EXIF, location | UC-01 |
| Import | `/import` | Choose folder, progress of upload and processing, history | UC-01 |
| Observation | `/observations/:id` | Photos, species, split/merge | UC-02, UC-03 |
| Review queue | `/review` | Bulk identification | UC-03 |
| Taxonomy | `/taxa`, `/taxa/:id` | Tree and species pages | UC-04 |
| Map | `/map` | Clustered observations | UC-04 |
| Account | `/account` | Change password, camera clock offsets | UC-02 |
| Admin users | `/admin/users` | Create/reset/deactivate users | UC-05 |

## API resources
M1; later milestones extend this table. Errors are `ProblemDetail` with a stable `code` (e.g. `auth.bad-credentials`,
`import.not-a-jpeg`) that the frontend translates. Unsafe requests need the `X-XSRF-TOKEN` header (value of the
`XSRF-TOKEN` cookie, ADR-0005). Another user's resource is always 404, never 403.

| Method | Path | Purpose | Who may call | FR |
|---|---|---|---|---|
| GET | `/api/public/info` | App name/version (template smoke endpoint) | anyone | - |
| GET | `/api/auth/csrf` | Sets the `XSRF-TOKEN` cookie if missing (204) | anyone | FR-001 |
| POST | `/api/auth/login` | Sign in (JSON), starts session; returns the user. 401 `auth.bad-credentials`, 429 `auth.too-many-attempts` (+ `Retry-After`) | anyone | FR-001 |
| POST | `/api/auth/logout` | Sign out (204) | user | FR-001 |
| GET | `/api/auth/session` | Who is signed in: `{user}` or `{user: null}`, always 200 (the SPA's start-up call) | anyone | FR-001 |
| GET | `/api/me` | Current user incl. `mustChangePassword` | user | FR-001 |
| PUT | `/api/me/password` | Change own password; signs out the user's other sessions (204) | user | FR-002 |
| GET/POST | `/api/admin/users` | List (paged) / create users with a temporary password | admin | FR-002 |
| POST | `/api/admin/users/{id}/password-reset` | Set a temporary password; signs the user out everywhere | admin | FR-002 |
| POST | `/api/admin/users/{id}/deactivate`, `/activate` | Lock/unlock an account (deactivation signs out; not oneself) | admin | FR-002 |
| DELETE | `/api/admin/users/{id}` | Delete an account with all its photos: rows at once, stored objects by a retried job (204; not oneself) | admin | NFR-006 |
| POST | `/api/imports` | Start an import batch (`label`, `filesFound`, `filesSkipped`) | user | FR-004 |
| POST | `/api/imports/{id}/check` | Send up to 1000 hashes, get those not yet stored (the rest count as duplicates) | owner | FR-005, FR-006 |
| PUT | `/api/imports/{id}/files/{sha256}` | Upload one file (raw body; `X-File-Path` URL-encoded, `X-File-Modified` epoch ms); server verifies hash + JPEG; 201 new / 200 duplicate | owner | FR-004 |
| POST | `/api/imports/{id}/finish` | Browser is done sending; processing continues | owner | FR-004 |
| GET | `/api/imports`, `/api/imports/{id}` | History and progress (upload counters + processing pending/done/failed) | owner | FR-008 |
| GET | `/api/photos?cursor=&size=` | Date-ordered page of own photos (keyset cursor, `nextCursor` null on the last page) | owner | FR-009 |
| GET | `/api/photos/{id}` | Photo details incl. EXIF and location | owner | FR-009 |
| GET | `/api/photos/{id}/thumb`, `/preview`, `/original` | Image bytes, streamed from storage through the app (`Cache-Control: private`, ETag = hash, 304); thumb/preview 404 until processed | owner | FR-008, FR-009 |

## Notifications and background work
| Trigger | Channel | Recipient | Content |
|---|---|---|---|
| File stored | job `PROCESS_PHOTO` | - | EXIF, thumbnails/previews (M1), embedding (M2+) |
| Import batch fully processed | job `GROUP_OBSERVATIONS` (M2) | - | Grouping, then GPS fill, then `IDENTIFY` (M3) |
| Any job state change | in-app progress (polling) | owner | "Przetworzono 4 100 z 8 300 zdjęć" |

## Open design questions
- Temporary password on account creation vs a one-time setup link shown to the admin (assumed: temporary password + forced change).
- Library home: photos or observations once M2 exists (assumed: observations, with a photo view toggle).
- Keyboard shortcuts for review (proposal: 1-3 accept suggestion, N name, U unknown, arrows move).

## Change history
| Date | Change |
|---|---|
| 2026-11-19 | API table updated to the implemented M1 backend |
| 2026-11-18 | First draft from kickoff |
