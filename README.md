# fotozielnik.pl

A Spring Boot + React web application developed with AI coding agents (Claude Code).
Vision, requirements and plans live in [`docs/`](docs/README.md); agent rules in [`AGENTS.md`](AGENTS.md).

## Requirements

| Tool | Why |
|---|---|
| JDK 25 | backend (Gradle toolchain; the wrapper downloads Gradle itself) |
| Docker (with Compose) | Postgres for `bootRun` (`compose.yml`) and Testcontainers in integration tests |
| Node.js 24+ / npm | frontend |
| bash, git, curl, jq, setsid | tools, git hooks and Claude Code hooks (Linux/macOS; Windows via WSL) |
| Claude Code | AI agent; picks up `CLAUDE.md`, `.claude/settings.json`, `.claude/skills/` |
| `gh` (optional) | create the GitHub repo from `tools/new-project.sh --github` |

First time in a clone: `cd frontend && npm ci && npx playwright install chromium`, and
`git config core.hooksPath .githooks` (done for you by `init-project.sh`).

## Daily use

| Task | How |
|---|---|
| Run the app | `tools/dev.sh up` -> http://localhost:5173 (Vite, proxies `/api` to Spring on :8080). `tools/dev.sh down` to stop. |
| Or in two terminals | `cd backend && ./gradlew bootRun` (starts Postgres via compose) and `cd frontend && npm run dev` |
| Fast static check | `tools/check.sh` (Claude runs it automatically; `--fix` formats Java) |
| Tests | `tools/test.sh` (backend + frontend), `tools/test.sh e2e`, `tools/test.sh all`, `--unit` without Docker |
| Screenshot a page | `tools/look.sh /some/path --name shot` -> `captures/shot.png` + console/network errors |
| Regenerate API types | `cd frontend && npm run api:types` (backend running) |
| API docs (dev) | http://localhost:8080/swagger-ui.html |
| Repo lint | `tools/lint-repo.sh` (runs on staged files at every commit) |
| Define/refresh the project | `/kickoff` in Claude Code |
| Verify before "done" | `/verify` in Claude Code |
| Record a decision | `/adr` in Claude Code |

**CI** (`.github/workflows/ci.yml`) runs the same `tools/` scripts on pushes to `main` and on pull requests.

**Git hooks** live in `.githooks/`: pre-commit = repo lint (immutable Flyway migrations, secrets, generated
and large files, conflict markers), pre-push = static check + unit tests. Emergency bypass: `--no-verify`.

---

## Using this repository as a template

This repo is a **project template**: a Spring Boot 4 + Vite/React/TypeScript monorepo plus agent
instructions, docs skeleton, verification tools and Claude Code hooks.

### New project in one command
From the template checkout (must be a git repository):
```bash
tools/new-project.sh --name "Order Desk" --group es.potatogam --github
```
Creates `../order-desk`: copies the template's files, runs `init-project.sh` (Java package
`es.potatogam.orderdesk`, artifact `order-desk`, placeholders, fresh git, hooks), installs frontend
dependencies and Chromium, adds a `template` remote, makes the initial commit (recording the template
commit), and optionally creates + pushes a private GitHub repo (`--github`, needs `gh`).
Options: `--artifact`, `--package`, `--folder`, `--parent`, `--owner`, `--public`, `--no-install`.
Then start Claude Code in the new folder and run **`/kickoff`**.

Manual alternative: copy or `git clone` the template, then inside the copy run
`tools/init-project.sh --name "<Name>" --group <group> --reset-git` and add your own remote.

### Pulling later template improvements into a project
```bash
git fetch template
git checkout template/main -- tools .claude .githooks .github AGENTS.md CLAUDE.md   # tooling only, never docs/ or code
git diff --staged                                                           # review, then commit
```
(`tools/new-project.sh` arrives this way too - delete it in projects; it only makes sense in the template.)
Projects usually customise `AGENTS.md`; review that file's diff by hand instead of taking it wholesale.

### What the template contains

```
AGENTS.md / CLAUDE.md        agent instructions (tool-neutral / Claude-specific)
docs/00-05, docs/adr/        kickoff questionnaire, vision, requirements, product design, architecture, roadmap, ADRs
backend/                     Spring Boot 4 (webmvc, JPA, Flyway, Security, Validation, Actuator, springdoc),
                             Error Prone + NullAway, Spotless, Testcontainers; smoke endpoint + slice/IT tests
frontend/                    Vite + React + TS strict, oxlint, Vitest, Playwright (smoke e2e + look.mjs),
                             API client with ProblemDetail errors, types generated from OpenAPI
compose.yml                  local Postgres (started by bootRun)
tools/                       check, test, look, dev, lint-repo, init-project, new-project (bash; shared _lib.sh)
.githooks/                   pre-commit repo lint, pre-push check + unit tests
.github/workflows/ci.yml     CI: repo lint (vs. base branch), backend incl. Testcontainers, frontend, e2e +
                             API-contract drift check; uploads reports, screenshots and server logs
.claude/settings.json        hooks + permissions
.claude/hooks/               guards (generated files, migrations, secrets, destructive shell), post-edit checks,
                             end-of-turn static check, session-start status
.claude/skills/              /kickoff, /verify, /adr
.gitignore / .gitattributes / .editorconfig
```

### Maintaining the template
Improve the template in its own repo and keep it generic: no project-specific code or docs content,
placeholders (`fotozielnik.pl`, `Status: TEMPLATE`) intact, base package `com.example.app`.
After changing tooling, check the template still passes: `tools/check.sh && tools/test.sh all && tools/lint-repo.sh`.
Projects created earlier do not update automatically - port improvements as shown above.
