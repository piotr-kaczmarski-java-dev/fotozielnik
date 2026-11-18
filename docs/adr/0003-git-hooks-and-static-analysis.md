# ADR-0003: Fail fast with versioned git hooks, a repo lint, Error Prone/NullAway and Spotless

- Status: Accepted
- Date: 2026-11-18
- Related: ADR-0001

## Context
The Claude Code hooks only guard agent edits. Human commits, and anything changed outside the agent,
reached the repository unchecked. The costly mistakes in a Spring web app are mostly invisible to the
compiler: an edited, already-applied Flyway migration (checksum failure on every existing database),
committed secrets or `.env` files, committed build output, and null-handling bugs.

## Decision
1. `tools/lint-repo.sh` checks immutable Flyway migrations (modified/deleted/renamed vs `HEAD`), migration
   naming and duplicate versions, secret patterns and `.env` files, generated files, files over 1 MB and
   conflict markers. ~1 s, no build.
2. Versioned hooks in `.githooks/` (`core.hooksPath`, set by `init-project.sh`): pre-commit = repo lint on
   staged files; pre-push = `tools/check.sh` + unit tests without Docker (exit 3 = could not run -> push
   allowed).
3. Error Prone on every compile; NullAway (JSpecify mode) as an **error** for main code under the base
   package - Spring Framework 7 is itself JSpecify-annotated, so nullness flows through framework calls.
4. Spotless with palantir-java-format; the end-of-turn hook applies it, pre-push only checks it.
5. Frontend: TypeScript `strict` + `noUncheckedIndexedAccess`, oxlint.
6. CI (`.github/workflows/ci.yml`, GitHub Actions) runs the same `tools/` scripts: repo lint with
   `--base` (the migration check compares the whole branch with its target), backend check + all tests
   (runners have Docker), frontend check + tests + build, and e2e: Playwright, a `look.sh` screenshot, and
   a check that the committed `schema.gen.ts` matches the backend's spec. Reports, captures and server logs
   are uploaded as artifacts.

## Alternatives considered
| Option | Pros | Cons |
|---|---|---|
| pre-commit framework (Python) | Ecosystem | Extra runtime on every machine |
| Checkstyle/PMD/SpotBugs | Broad rule sets | Slower, noisy; Error Prone catches more real bugs per warning |
| google-java-format | Canonical | 2-space indent; palantir reads better at 120 cols |
| Integration tests in pre-push | Stronger gate | Needs Docker; minutes - belongs in CI |
| Hooks only, no CI | Simpler | Hooks are bypassable (`--no-verify`) and never run `*IT`/e2e; nothing checks the branch as a whole |

## Consequences
- Broken migrations, secrets and generated files are stopped before they reach history; broken code
  before it reaches the remote.
- Clones other than template-initialised ones must run `git config core.hooksPath .githooks` once.
- CI is the only place every check runs together; a red job names the `tools/` command to reproduce it.
- NullAway forces explicit `@Nullable` on fields/returns/params that can be null - intended.
