# AGENTS.md - fotozielnik.pl

Instructions for AI coding agents (Claude Code, Codex, Cursor, Copilot...) working in this repo.
Humans: this is also the fastest onboarding doc. Keep it short, current and true.

## Project

- **What:** {{ONE_LINE_PITCH}} (see `docs/01-vision.md`)
- **Stack:** Spring Boot 4 / Java 25 / Gradle Kotlin DSL (`backend/`), PostgreSQL + Flyway,
  Vite + React + TypeScript (`frontend/`), Playwright. Versions: `backend/gradle/libs.versions.toml`,
  `backend/build.gradle.kts`, `frontend/package.json`.
- **Current milestone:** see "Now" in `docs/05-roadmap.md`.

## Sources of truth (read before non-trivial work)

| Question | File |
|---|---|
| Why does this app exist, what is out of scope? | `docs/01-vision.md` |
| What must it do / how fast / how secure? | `docs/02-requirements.md` (IDs `FR-xxx`, `NFR-xxx`) |
| Who does what; domain model; screens; API resources? | `docs/03-product-design.md` |
| How is the code organised and why? | `docs/04-architecture.md`, `docs/adr/` |
| What are we doing now / next? | `docs/05-roadmap.md` |

If a doc still says `Status: TEMPLATE`, the project has not been kicked off: ask the user to run
the kickoff (Claude Code: `/kickoff`) instead of guessing requirements.
If a request contradicts a doc, say so and ask; do not silently diverge. Update docs in the same
change when behaviour or decisions change.

## Repository layout

```
backend/                              Spring Boot app (own Gradle wrapper: cd backend && ./gradlew ...)
  src/main/java/<base package>/       Application, config/, shared/, one package per feature
  src/main/resources/application.yml  config; `prod` profile at the bottom
  src/main/resources/db/migration/    Flyway migrations V<n>__<desc>.sql - immutable once committed
  src/test/java/...                   *Test (no Docker), *IT (Testcontainers Postgres)
frontend/                             Vite + React + TS
  src/api/                            client.ts (fetch + ProblemDetail), schema.gen.ts (GENERATED), types.ts
  e2e/                                Playwright tests (*.spec.ts) + look.mjs (screenshot tool)
compose.yml                           local Postgres, started automatically by bootRun
docs/                                 vision, requirements, product design, architecture, roadmap, ADRs
tools/                                check, test, look, dev, lint-repo, init-project (bash)
.githooks/                            pre-commit repo lint, pre-push check + unit tests
.github/workflows/ci.yml              CI: repo lint, backend, frontend, e2e + API-contract drift check
.claude/                              Claude Code settings, hooks, skills
```

## Commands (bash, from the repo root)

| Purpose | Command | Notes |
|---|---|---|
| Static check (seconds) | `tools/check.sh [backend\|frontend] [--fix]` | Java compile + Error Prone/NullAway + Spotless; TS typecheck + oxlint. Runs automatically after each turn. `--fix` formats Java. |
| Tests | `tools/test.sh [backend\|frontend\|e2e\|all] [--unit] [--filter <pattern>]` | Default backend + frontend. `--unit` skips `*IT` (no Docker). `e2e` starts the stack if needed. |
| Look at a page | `tools/look.sh [/path] [--name shot] [--mobile] [--eval '<js>'] [--wait <css>]` | Screenshot to `captures/<shot>.png` + console errors + HTTP >= 400. **Read the PNG.** |
| Dev stack | `tools/dev.sh up\|down\|status\|logs [backend\|frontend]` | Spring :8080 (+ compose Postgres), Vite :5173. Logs in `logs/`. |
| Regenerate API types | `cd frontend && npm run api:types` | Backend must be running. After any controller/DTO change. |
| Repo lint (~1 s) | `tools/lint-repo.sh [--staged \| --base <ref>]` | Immutable migrations, secrets, generated files, big files, conflict markers. `--base origin/main` checks a whole branch. |
| Single backend test | `tools/test.sh backend --filter '*OrderServiceTest'` | Gradle `--tests` pattern. |

Exit codes: 0 ok, 1 failed (fix it), 3 could not run (read the message - e.g. no Docker, no node_modules).

**Git hooks** (`.githooks/`, enabled via `core.hooksPath`): pre-commit runs the repo lint on staged files,
pre-push runs the static check and the unit tests. Fix what they report - do not use `--no-verify` unless
the user tells you to.

**CI** (`.github/workflows/ci.yml`) runs the same tools on every push/PR, plus a check that the committed
`schema.gen.ts` matches the backend. A red CI job is reproduced locally with the `tools/` command it names.

## Hard rules (violating these causes silent or irreversible breakage)

1. **Committed Flyway migrations are immutable.** Never edit, rename or delete a committed
   `V<n>__*.sql` - databases that applied it fail the checksum or drift. Change the schema with a new file,
   next free number. The schema is owned by Flyway: keep `ddl-auto: validate`, never `update`.
2. **Never edit generated files:** `frontend/src/api/schema.gen.ts` (run `npm run api:types`),
   `package-lock.json` (use npm), `backend/build/`, `node_modules/`, `dist/`, the Gradle wrapper jar.
3. **Security is deny-by-default.** A new endpoint is reachable only if `SecurityConfig` says so on purpose;
   never widen `permitAll` patterns to make something work. Every endpoint that touches user data checks
   *which* user may touch *which* record (authorization, not only authentication).
4. **Entities never cross the HTTP boundary.** Controllers take and return DTO records; map in the service
   or controller. Validate every request body (`@Valid` + constraints on the record).
5. **No secrets in the repo** - not in `application.yml`, tests, or docs. Use environment variables
   (`${DB_PASSWORD}`) and `.env.example` with placeholders. `compose.yml`'s dev password is the only exception.
6. **API change = contract change.** When a request/response shape changes, regenerate the frontend types
   in the same change and fix every TypeScript error it causes.
7. New dependencies (Gradle or npm), new infrastructure (Redis, queues...) and changes to `compose.yml`
   or the Gradle wrapper: say so explicitly in your summary with the reason; significant ones get an ADR.

## Code conventions

**Backend**
- Package by feature (`<base>.orders`, `<base>.users`); classes package-private by default. Layers inside a
  feature: `XController` (HTTP) -> `XService` (`@Transactional`, rules) -> `XRepository` (Spring Data).
  Features call each other's services, never each other's repositories.
- Constructor injection with `final` fields; no `@Autowired` fields in main code. Config via
  `@ConfigurationProperties` records.
- Non-null by default (NullAway is a compile error): mark what can be null with
  `org.jspecify.annotations.Nullable`; prefer `Optional` for "maybe absent" return values of finders.
- Errors: throw domain exceptions (e.g. `NotFoundException`) and map them once in `ApiExceptionHandler` to
  `ProblemDetail`. No try/catch-and-return-null.
- Persistence: `open-in-view` is off - fetch what a response needs inside the transaction (fetch joins,
  `@EntityGraph`, DTO projections). No repository calls in loops (N+1). Paginate list endpoints.
- Logging via SLF4J (`LoggerFactory.getLogger`), never `System.out`; never log secrets or personal data.
- Comments explain *why*, not *what*. Public types used across features get Javadoc.

**Frontend**
- TypeScript strict; no `any` (use `unknown` + narrowing). API calls only through `src/api/` with generated
  types. Components are function components; hooks follow the rules of hooks.
- Every async view handles loading, error (show the ProblemDetail message) and empty states.
- Accessible by default: semantic elements, labels for inputs, `role`/`data-testid` for test hooks.

## Workflow for every change

1. Read the relevant docs/requirements; restate the goal and acceptance criteria if unclear.
2. Plan the vertical slice: migration -> entity/repository -> service -> controller/DTOs -> security rule ->
   `npm run api:types` -> UI -> tests at each level. Touching 3+ files: show the plan first.
3. Write or extend tests first (or alongside): unit/slice tests for logic and controllers, an `*IT` through
   the database, an e2e test for user-visible flows.
4. Implement in small steps. Keep diffs focused; no drive-by refactors.
5. Verify: `tools/check.sh` passes; `tools/test.sh` passes; for UI work `tools/look.sh` and look at the
   capture; for flows `tools/test.sh e2e`.
6. Update docs: requirement status in `02-requirements.md`, API table in `03-product-design.md`, ADR for
   any architectural decision, roadmap if scope moved.
7. Summarise: what changed, how it was verified (commands + results, capture paths), dependencies added,
   and **what the human must still check** (real browser feel, copy, real external services).

## Definition of done

- Static check passes (compile + NullAway + format + typecheck + lint).
- Tests for new logic exist and pass, including an `*IT` when persistence or security changed
  (or it is stated that Docker was unavailable).
- API types regenerated if the contract changed; visible changes looked at via `tools/look.sh`.
- No rule above violated; docs updated; remaining manual checks listed.
