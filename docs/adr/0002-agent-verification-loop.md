# ADR-0002: Let agents verify their own work - Testcontainers, Playwright e2e and screenshots

- Status: Accepted
- Date: 2026-11-18
- Related: ADR-0001, NFR-011

## Context
A compiling change is not a working change. In a web app the expensive failures appear only when the
layers meet: a migration that does not match the entity, a security rule that blocks a new endpoint, a
frontend that renders an error state or throws in the console. An agent that cannot observe these
declares work done too early.

## Decision
1. `tools/test.sh backend` runs `*IT` tests against real PostgreSQL via Testcontainers
   (`@ServiceConnection`), so Flyway migrations and Hibernate validation are exercised on every run.
2. `tools/test.sh e2e` runs Playwright against the real stack; Playwright starts `bootRun` (which starts
   compose's Postgres) and Vite when they are not already running.
3. `tools/look.sh <path>` opens one page in headless Chromium, saves `captures/<name>.png` and reports
   console errors, page errors and HTTP >= 400 responses; `--eval` asserts on live DOM state.
   Agents read the PNG. It starts the stack if needed and stops what it started.
4. `tools/dev.sh` runs each dev server in its own process group, so it can always stop what it started
   (including the Spring JVM that Gradle forks).
5. The end-of-turn hook runs only the static check (seconds); tests and looks run on demand (`/verify`).

## Alternatives considered
| Option | Pros | Cons |
|---|---|---|
| Playwright MCP server / browser extension | Interactive browsing | Not scriptable from hooks; per-machine setup |
| Tests in the Stop hook | Stronger gate | Tens of seconds after every turn; needs Docker |
| Screenshot via Vitest browser mode | One runner | Does not exercise the real backend |

## Consequences
- Agents can see visible changes and catch integration failures themselves; the human checks feel,
  copy and accessibility with assistive tech.
- About 150 MB for the Playwright Chromium download per machine (`npx playwright install chromium`).
