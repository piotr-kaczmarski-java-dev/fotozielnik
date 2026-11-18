@AGENTS.md

# Claude Code specifics

## Automation already in place (`.claude/settings.json`)

| Hook | When | What it does |
|---|---|---|
| `session-start.sh` | session start | Java/toolchain match, Docker, node_modules + Playwright browser, whether the dev stack is up, unfilled docs, git + git-hooks state. |
| `guard-files.sh` | before Edit/Write | **Denies** edits to build output, `node_modules/`, `schema.gen.ts`, `package-lock.json`, the wrapper jar; **asks** before editing a committed Flyway migration, `.env`, the Gradle wrapper properties, or the guard rails themselves. |
| `guard-shell.sh` | before Bash | **Denies** deleting top-level project folders or Flyway migrations; **asks** before `docker compose down -v`, volume prunes, `flyway clean`, destructive SQL, `git clean -x`, `reset --hard`, force push, `--no-verify`, adding/removing npm packages, publishing. |
| `post-edit.sh` | after an edit | Marks backend/frontend as changed; flags package/folder and type/file mismatches, field injection, `System.out`, `javax.*` and non-JSpecify `@Nullable`, `ddl-auto` other than validate, badly named or out-of-order migrations, and reminds you to regenerate API types after controller changes. |
| `stop-verify.sh` | when you finish a turn | If code changed, runs `tools/check.sh --fix` for the changed side(s) - **Java formatting is applied automatically**; compile/NullAway/type/lint errors block finishing (max 3 attempts). |

If a hook blocks you, do not work around it - fix the cause or ask the user.
Hook state lives in `.claude/state/` (gitignored).

## Skills

- `/kickoff` - interview the user and fill `docs/` for a new project. Run first in every new project.
- `/verify` - full verification pass (static check, API types, tests, e2e, screenshot, rule review) before declaring work done.
- `/adr` - record an architecture decision in `docs/adr/`.

## Tips

- After `tools/look.sh`, open the capture with the Read tool (`captures/<name>.png`) - you can see it.
- JavaScript for `look.sh --eval` that contains quotes: write it to a file in your scratchpad and pass `--eval-file`.
- `tools/dev.sh up` before several looks / type regenerations in a row; `tools/dev.sh down` when done.
  Never kill the user's own servers: `dev.sh down` only stops what `dev.sh` started.
- Gradle output is long; prefer `tools/check.sh` / `tools/test.sh`, which print only what matters.
  Full test reports: `backend/build/reports/tests/test/index.html`.
- Use the AskUserQuestion tool for product and design decisions; propose a recommended default.
- Personal overrides: `CLAUDE.local.md` and `.claude/settings.local.json` (both gitignored).
