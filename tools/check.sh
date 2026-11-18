#!/usr/bin/env bash
# Fast static check (seconds): compiles backend main+test with Error Prone/NullAway, checks formatting,
# typechecks and lints the frontend. No tests, no Docker. Claude runs it automatically at the end of a turn.
#
# Usage: tools/check.sh [backend|frontend|all] [--fix]
#   --fix   apply formatting (spotlessApply) before checking instead of only reporting it
set -uo pipefail
source "$(dirname "$0")/_lib.sh"

target=all fix=0
for a in "$@"; do
    case "$a" in
        backend|frontend|all) target="$a" ;;
        --fix) fix=1 ;;
        -h|--help) sed -n '2,7p' "$0"; exit 0 ;;
        *) echo "unknown argument: $a"; exit 3 ;;
    esac
done

rc=0
if [[ "$target" == backend || "$target" == all ]]; then
    if ! have java; then
        fail "backend: java not on PATH"; rc=3
    else
        start=$SECONDS
        if (( fix )); then tasks=(spotlessApply compileJava compileTestJava); else tasks=(compileJava compileTestJava spotlessCheck); fi
        if out=$(gradle "${tasks[@]}" 2>&1); then
            ok "backend: compile (Error Prone + NullAway) + format ($((SECONDS - start)) s)"
            printf '%s\n' "$out" | filter_noise | grep -E 'warning:' | sed 's/^/      /' | head -20
        else
            fail "backend: $(IFS=' '; echo "${tasks[*]}")"
            printf '%s\n' "$out" | filter_noise | sed '/^FAILURE: Build failed/,$d' | grep -vE '^[[:space:]]*$' | head -60
            grep -q 'spotless' <<<"$out" && info "formatting only? run: tools/check.sh backend --fix"
            rc=1
        fi
    fi
fi

if [[ "$target" == frontend || "$target" == all ]]; then
    if ! frontend_deps_ok; then
        fail "frontend: node_modules missing - run: (cd frontend && npm ci)"; (( rc == 0 )) && rc=3
    else
        start=$SECONDS
        if out=$(cd "$FRONTEND" && npm run -s typecheck 2>&1 && npm run -s lint 2>&1); then
            ok "frontend: typecheck + lint ($((SECONDS - start)) s)"
            printf '%s\n' "$out" | grep -iE 'warn' | sed 's/^/      /' | head -20
        else
            fail "frontend: typecheck/lint"
            printf '%s\n' "$out" | head -60
            rc=1
        fi
    fi
fi
exit $rc
