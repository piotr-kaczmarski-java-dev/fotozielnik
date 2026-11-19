# Kickoff questionnaire

Status: DRAFT

Answer these once at project start (Claude Code: run `/kickoff` to be interviewed).
Short answers are fine; "don't know yet" is a valid answer - it becomes an open question.
The answers are then distilled into docs 01-05.

## 1. Vision
1. One-sentence pitch ("It's a ___ that helps ___ to ___").
2. What problem does it solve, for whom? How do they solve it today?
3. Three product principles (the tie-breakers for every decision, e.g. "fast over flexible").
4. Why would someone choose this over the alternative (including "do nothing")?
5. Explicit non-goals (what this app will deliberately NOT do).
6. How will we know it worked (users, usage, revenue, a date)?

## 2. Users and domain
7. User types / roles and what each may do (anonymous visitor, user, admin...).
8. The 3-5 most important things users do (use cases), in priority order.
9. The core domain objects and how they relate (e.g. "a Team has Projects; a Project has Tasks").
10. External systems to integrate (payments, email, maps, third-party APIs, an existing database).
11. Notifications: email, push, in-app? Triggered by what?
12. Anything real-time (live updates, chat) or offline?

## 3. Quality targets
13. Expected users at launch and in a year; concurrent users at peak; data volume.
14. Response-time expectations (e.g. p95 API < 300 ms) and acceptable downtime.
15. Browsers and devices (desktop only? mobile web? minimum widths?).
16. Accessibility commitment (e.g. WCAG 2.2 AA) and languages at launch.

## 4. Security and data
17. How do users sign in (email + password, magic link, Google/GitHub, company SSO/OIDC, none)?
18. Personal or sensitive data stored? Which jurisdictions (GDPR...)? Retention and deletion rules.
19. Audit needs (who changed what, when)? Data export/import?
20. Payments or file uploads?

## 5. Scope and constraints
21. Team: who, which skills, how many hours per week?
22. Goal: learning project / portfolio piece / side business / commercial product / client work?
23. Deadlines: walking skeleton, MVP, launch.
24. Monthly budget for hosting and services.
25. Business model (free, subscription, one-off, internal tool).

## 6. Tech
26. Hosting and deployment (a VPS with Docker, a PaaS such as Fly.io/Render/Railway, a cloud provider, Kubernetes)?
27. Serve the frontend from the Spring jar (one deployable) or as separate static hosting/CDN?
28. Extra infrastructure needed now (Redis, message queue, object storage, full-text search)?
29. Frontend libraries already preferred (router, data fetching, UI kit/CSS approach, forms)?
30. Background jobs or scheduled tasks?
31. Observability (logs, metrics, error tracking) and backups.
32. Any AI/LLM features? If yes: provider, data-privacy limits, cost ceiling.

## Answers

> Filled in by `/kickoff` on 2026-11-18 from the owner's written spec plus a short interview.
> `(assumed)` = default proposed by the agent, not yet confirmed by the owner.

### 1. Vision
1. A self-hosted photo library ("Immich for wild plants") that helps amateur botanists turn huge unsorted
   archives of wild-plant photos into identified, browsable observations.
2. Amateur botanists (first: the owner's father and his friends - older, not technical) have 10-20 GB /
   tens of thousands of DSLR photos each, in unsorted folder dumps. Today: folders by date, manual
   identification, no search by species/place. General photo apps (Immich, Google Photos) do not know taxonomy
   or the concept of an observation.
3. Principles: (a) simple for older, non-technical users; (b) automate the bulk, let the human correct;
   (c) trivial to host and portable (one compose file, S3-compatible storage).
4. Automatic grouping + species ID for Central European flora, Polish UI and names, private, self-hosted.
5. Non-goals (MVP): sharing/social, mobile app, RAW files, non-vascular plants/fungi, iNaturalist/GBIF export,
   phenology/life lists, payments, open registration, email notifications.
6. Success (assumed): father + at least 3 friends have imported their full archives and use the review
   queue regularly; >= 70 % of observations get a correct auto-suggestion in the top 3.

### 2. Users and domain
7. Roles: User (own private library only), Admin (creates accounts, resets passwords; also a user).
   No anonymous access beyond the login page.
8. Use cases in order: import folder tree; automatic grouping into observations (split/merge); automatic
   species ID + review queue + bulk edit; browse by taxonomy / map / date; fill missing GPS.
9. Domain: User has Photos (from ImportBatches); Photos are grouped into Observations; an Observation has
   one Taxon (or none) plus ranked IdSuggestions; Taxon tree (family > genus > species) with names
   (Latin + Polish) and sensitivity; per-model Photo embeddings and Taxon prototypes.
10. External: GBIF backbone taxonomy (offline import), BioCLIP model (offline ONNX export), map tiles (OSM).
11. Notifications: none in MVP; in-app import/processing progress only.
12. Real-time: no (polling of job progress is enough). Offline: no.

### 3. Quality targets
13. Launch: ~5-10 users, 1-3 concurrent; ~30k photos / 20 GB per user -> ~300k photos, ~200 GB (assumed).
14. Interactive pages p95 < 500 ms; imports/ML may run overnight; best-effort availability, ~99 % (assumed).
15. Desktop first (folder import needs a PC); tablets usable for browsing; phones work but are not designed for.
    Last 2 versions of Chrome/Firefox/Edge/Safari.
16. Polish at launch, i18n-ready. Accessibility: WCAG 2.2 AA, large type (>= 18 px base), large targets (assumed).

### 4. Security and data
17. Email + password, accounts created by an admin; session cookie. No self-registration, no email.
18. Personal data: email, name, photos with GPS (location data). GDPR applies; data hosted in the EU;
    user deletion removes all photos and rows (assumed).
19. No audit log in MVP. Export of originals is implicit (they are kept unchanged) (assumed).
20. File uploads: yes, large (JPEG only in MVP). No payments.

### 5. Scope and constraints
21. Solo developer with AI agents, ~5-10 h/week.
22. Family tool now; keep a commercial hosted product possible (portable architecture, no billing in MVP).
23. No hard deadlines; rough targets in 05-roadmap (assumed).
24. ~EUR 20-40/month for VPS + storage (assumed).
25. Free for the first users; business model decided later.

### 6. Tech
26. EU VPS (e.g. Hetzner/OVH) with Docker Compose: app + Postgres + Garage (+ Caddy for HTTPS).
27. Frontend built into the Spring jar (one deployable image).
28. Object storage: S3-compatible (Garage locally/on the VPS; MinIO archived). Postgres with PostGIS + pgvector (custom image).
    No Redis, no message broker: job queue in Postgres.
29. React Router, TanStack Query, i18next, MapLibre GL, plain CSS with design tokens - no UI kit (assumed).
30. Yes: Postgres-backed job queue for import processing, ML, grouping.
31. Structured logs, `/actuator/health`; nightly `pg_dump` + object-storage sync to off-site storage (assumed).
32. Local ML only: BioCLIP image encoder via ONNX Runtime in the JVM; no external AI APIs, no Python at runtime.
    Java 25 (template) instead of the spec's Java 21 - confirmed by the owner.

## Open questions

- Q1: Polish vernacular-name coverage in the GBIF backbone - enough, or merge a Polish checklist (licence)?
- Q2: BioCLIP vs BioCLIP 2: accuracy on Central European flora vs CPU cost; verify model licences.
- Q3: Grouping thresholds (time gap, similarity, distance) - tune on the father's real archive.
- Q4: Time zone of EXIF timestamps without offset: assume Europe/Warsaw, allow per-import override?
- Q5: RAW support - only if users ask for it.
- ~~Q6: MinIO replacement~~ - resolved 2026-11-18: Garage (ADR-0007).
