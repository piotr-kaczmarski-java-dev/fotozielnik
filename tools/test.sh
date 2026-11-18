#!/usr/bin/env bash
# Runs tests.
#   backend   JUnit: unit + web-slice tests and *IT integration tests (Testcontainers -> needs Docker)
#   frontend  Vitest unit/component tests
#   e2e       Playwright browser tests against the running stack (starts backend/frontend if needed)
#
# Usage: tools/test.sh [backend|frontend|e2e|all] [--unit] [--filter <pattern>]
#   default target: backend + frontend (all = also e2e)
#   --unit     backend: skip *IT (no Docker needed)
#   --filter   backend: Gradle --tests pattern (e.g. '*OrderServiceTest'); frontend/e2e: test-name grep
set -uo pipefail
source "$(dirname "$0")/_lib.sh"

targets=() unit=0 filter=""
while (( $# )); do
    case "$1" in
        backend|frontend|e2e) targets+=("$1") ;;
        all) targets=(backend frontend e2e) ;;
        --unit) unit=1 ;;
        --filter) filter="${2:-}"; shift ;;
        -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1"; exit 3 ;;
    esac
    shift
done
(( ${#targets[@]} )) || targets=(backend frontend)

rc=0
for t in "${targets[@]}"; do
    start=$SECONDS
    case "$t" in
    backend)
        task=test
        if (( unit )); then task=unitTest
        elif ! docker_ok; then warn "backend: Docker not available - running unit tests only (*IT skipped)"; task=unitTest; (( rc == 0 )) && rc=3
        fi
        args=("$task"); [[ -n "$filter" ]] && args+=(--tests "$filter")
        if out=$(gradle "${args[@]}" 2>&1); then
            n=$(cat "$BACKEND/build/test-results/$task"/*.xml 2>/dev/null | grep -o 'tests="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$0} END {print s}')
            ok "backend: $task (${n:-?} tests, $((SECONDS - start)) s)"
        else
            fail "backend: $task"
            printf '%s\n' "$out" | filter_noise | grep -E 'FAILED|Exception|Error|expected|but was|at .*Test' | grep -v '^\s*at org\.junit' | head -40
            info "report: backend/build/reports/tests/$task/index.html"
            rc=1
        fi ;;
    frontend)
        frontend_deps_ok || { fail "frontend: node_modules missing - run: (cd frontend && npm ci)"; rc=3; continue; }
        args=(run -s test --); [[ -n "$filter" ]] && args+=(-t "$filter")
        if out=$(cd "$FRONTEND" && npm "${args[@]}" 2>&1); then
            ok "frontend: vitest ($(grep -oE 'Tests +[0-9]+ passed' <<<"$out" | grep -oE '[0-9]+') tests, $((SECONDS - start)) s)"
        else
            fail "frontend: vitest"; printf '%s\n' "$out" | tail -40; rc=1
        fi ;;
    e2e)
        frontend_deps_ok || { fail "e2e: node_modules missing - run: (cd frontend && npm ci)"; rc=3; continue; }
        if ! backend_up && ! docker_ok; then fail "e2e: backend not running and Docker unavailable (bootRun needs Postgres)"; rc=3; continue; fi
        args=(run -s e2e --); [[ -n "$filter" ]] && args+=(-g "$filter")
        if out=$(cd "$FRONTEND" && npm "${args[@]}" 2>&1); then
            ok "e2e: playwright ($(grep -oE '[0-9]+ passed' <<<"$out" | tail -1), $((SECONDS - start)) s)"
        else
            fail "e2e: playwright"; printf '%s\n' "$out" | tail -40
            info "report: frontend/playwright-report/index.html; traces in frontend/test-results/"
            rc=1
        fi ;;
    esac
done
exit $rc
