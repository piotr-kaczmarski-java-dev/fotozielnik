# ADR-0001: Adopt the Spring + React + AI-agent project template baseline

- Status: Accepted
- Date: 2026-11-18
- Related: NFR-005, NFR-011

## Context
Web applications are developed with heavy use of AI coding agents (Claude Code). Agents produce code fast
but cannot see the running app and do not remember earlier sessions, so the project must give them fast,
automatic feedback, written-down intent, and guard rails around the things that break silently or
irreversibly in a Spring web app: applied database migrations, the API contract between backend and
frontend, security rules, secrets.

## Decision
1. Monorepo: `backend/` (Spring Boot 4, Java 25, Gradle Kotlin DSL + version catalog) and `frontend/`
   (Vite, React, TypeScript strict). One `compose.yml` for local infrastructure.
2. Backend packaged by feature; DTO records at the boundary; entities never leave the service layer;
   ProblemDetail errors; deny-by-default security; Flyway owns the schema (Hibernate validates).
3. PostgreSQL everywhere: compose for `bootRun`, Testcontainers for tests - no H2.
4. Verification tooling in `tools/` (bash): `check.sh` (static, seconds), `test.sh`, `look.sh`
   (Playwright screenshot), `dev.sh`, `lint-repo.sh`. Exit codes 0 ok / 1 failed / 3 could not run.
5. Claude Code hooks enforce the guard rails and run the static check at the end of each turn.
6. `AGENTS.md` is the tool-neutral instruction file; `CLAUDE.md` imports it and adds Claude specifics.
7. Requirements and decisions live in `docs/` with traceable IDs.
8. The authentication mechanism is deliberately NOT decided by the template (kickoff ADR).

## Alternatives considered
| Option | Pros | Cons |
|---|---|---|
| Separate repos for backend and frontend | Independent release cycles | Agent cannot change an API and its client in one change; contract drift |
| Next.js frontend | SSR, file routing | Second server runtime; fast-moving framework conventions confuse agents |
| Server-rendered Thymeleaf/htmx | One deployable, no API contract | Less suitable for rich client UIs; chosen per project if needed |
| H2 for tests | No Docker | Dialect differences hide real migration/query bugs |
| Maven | Ubiquitous | Gradle Kotlin DSL preferred by the owner; typed build scripts |

## Consequences
- Agents get compile/type feedback in seconds and cannot easily corrupt migrations or generated files.
- Docker is needed for integration tests, `bootRun` and e2e; without it only unit/slice tests run.
- Hook and tool scripts require bash, jq, curl and setsid (Linux/macOS; Windows via WSL or Git Bash).
