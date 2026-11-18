#!/usr/bin/env bash
# SessionStart: stdout becomes context for Claude. Keep it short and factual.
source "$(dirname "$0")/_lib.sh"
lines=("[Project status - from .claude/hooks/session-start.sh]")

want_java="$(grep -oP 'JavaLanguageVersion\.of\(\K[0-9]+' "$BACKEND/build.gradle.kts" 2>/dev/null)"
if have java; then
    jv="$(java -version 2>&1 | head -1 | grep -oP '"\K[0-9]+' )"
    [[ -n "$want_java" && "$jv" != "$want_java" ]] && lines+=("- Java on PATH is $jv but the toolchain wants $want_java: Gradle fails unless a JDK $want_java is installed.") \
        || lines+=("- Java $jv (toolchain ${want_java:-?}).")
else
    lines+=("- Java NOT on PATH: backend build unavailable.")
fi

if docker_ok; then lines+=("- Docker running: integration tests (*IT, Testcontainers) and bootRun (compose.yml Postgres) available.")
else lines+=("- Docker NOT available: run backend tests with 'tools/test.sh backend --unit'; bootRun/e2e/look need Docker.")
fi

if have node; then
    fe="node $(node -v)"
    frontend_deps_ok || fe+="; frontend/node_modules MISSING (run: cd frontend && npm ci)"
    compgen -G "$HOME/.cache/ms-playwright/chromium*" >/dev/null || fe+="; Playwright browser missing (run: cd frontend && npx playwright install chromium)"
    lines+=("- Frontend: $fe.")
else
    lines+=("- node NOT on PATH: frontend tools unavailable.")
fi

be="down"; backend_up && be="UP ($BACKEND_URL)"
fr="down"; frontend_up && fr="UP ($FRONTEND_URL)"
lines+=("- Dev stack: backend $be, frontend $fr. tools/look.sh starts/stops it as needed; tools/dev.sh up keeps it running.")

mapfile -t templ < <(grep -lE '^Status:[[:space:]]*TEMPLATE' "$ROOT"/docs/*.md 2>/dev/null | xargs -r -n1 basename)
(( ${#templ[@]} )) && lines+=("- Docs still unfilled (Status: TEMPLATE): ${templ[*]}. Suggest /kickoff before feature work.")

if git -C "$ROOT" rev-parse --git-dir >/dev/null 2>&1; then
    branch="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null)"
    dirty="$(git -C "$ROOT" status --porcelain 2>/dev/null | wc -l)"
    hp="$(git -C "$ROOT" config core.hooksPath)"
    g="- git: branch '$branch', $dirty uncommitted change(s)."
    [[ "$hp" != ".githooks" ]] && g+=" Git hooks NOT enabled (git config core.hooksPath .githooks)."
    lines+=("$g")
else
    lines+=("- Not a git repository yet: suggest tools/init-project.sh.")
fi
printf '%s\n' "${lines[@]}"
exit 0
