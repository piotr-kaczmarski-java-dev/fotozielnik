# Shared helpers for tools/*.sh and .claude/hooks/*.sh. Source it; do not execute.
# Exit-code convention for tools: 0 ok, 1 failed (fix it), 3 could not run (read the message).

project_root() {
    if [[ -n "${CLAUDE_PROJECT_DIR:-}" && -f "$CLAUDE_PROJECT_DIR/AGENTS.md" ]]; then
        printf '%s\n' "$CLAUDE_PROJECT_DIR"
    else
        (cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
    fi
}

ROOT="$(project_root)"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:5173}"
RUN_DIR="$ROOT/logs"          # dev server logs + pids (gitignored)

ok()   { printf 'OK    %s\n' "$*"; }
info() { printf '      %s\n' "$*"; }
warn() { printf 'WARN  %s\n' "$*"; }
fail() { printf 'FAIL  %s\n' "$*"; }
skip() { printf 'SKIP  %s\n' "$*"; }

have() { command -v "$1" >/dev/null 2>&1; }

backend_up()  { curl -sf -m 2 "$BACKEND_URL/actuator/health" >/dev/null 2>&1; }
frontend_up() { curl -sf -m 2 "$FRONTEND_URL" >/dev/null 2>&1; }
docker_ok()   { have docker && docker info >/dev/null 2>&1; }
frontend_deps_ok() { [[ -d "$FRONTEND/node_modules" ]]; }

# Runs Gradle in backend/ quietly. Usage: gradle <tasks/args...>
gradle() { (cd "$BACKEND" && ./gradlew --quiet --console=plain "$@"); }

# Filters JVM noise lines that are not actionable.
filter_noise() { grep -vE 'Sharing is only supported for boot loader classes|^Note: .*(unchecked|deprecat)|^Note: Recompile with -Xlint' || true; }
