#!/usr/bin/env bash
# Starts/stops the local stack in the background: Spring Boot (bootRun; it also starts compose.yml's
# Postgres) and the Vite dev server. Logs: logs/backend.log, logs/frontend.log.
#
# Usage: tools/dev.sh [up|down|status|logs [backend|frontend]]
#   up      start whatever is not already running, wait until both answer (default)
#   down    stop what this script started (Postgres keeps running; `docker compose stop` to stop it)
set -uo pipefail
source "$(dirname "$0")/_lib.sh"
mkdir -p "$RUN_DIR"

# Each server runs in its own process group (setsid); the pid file holds the group id, so `down` also
# stops children (the Spring JVM forked by Gradle, Vite under npm).
pidfile() { printf '%s/%s.pid\n' "$RUN_DIR" "$1"; }
alive()   { [[ -f "$(pidfile "$1")" ]] && pgrep -g "$(cat "$(pidfile "$1")")" >/dev/null 2>&1; }

start_group() { # name dir cmd... : new session; the leader writes its own pid (= process group id)
    local name="$1" dir="$2"; shift 2
    (cd "$dir" && setsid --fork bash -c 'echo $$ >"$0"; exec "$@"' "$(pidfile "$name")" "$@" \
        >"$RUN_DIR/$name.log" 2>&1 </dev/null)
    local _; for _ in {1..20}; do [[ -s "$(pidfile "$name")" ]] && return 0; sleep 0.1; done
}

wait_for() { # name check-fn timeout-s
    local i
    for (( i = 0; i < $3; i++ )); do
        "$2" && return 0
        if ! alive "$1"; then fail "$1 exited - see logs/$1.log:"; tail -25 "$RUN_DIR/$1.log"; return 1; fi
        sleep 1
    done
    fail "$1 not ready after $3 s - see logs/$1.log"; return 1
}

up() {
    local rc=0
    if backend_up; then ok "backend already up ($BACKEND_URL)"
    else
        docker_ok || { fail "Docker is not available; bootRun needs it for Postgres (compose.yml)"; return 3; }
        if [[ ! -f "$ROOT/.env" && -f "$ROOT/.env.example" ]]; then
            cp "$ROOT/.env.example" "$ROOT/.env" && info "created .env from .env.example (dev admin login)"
        fi
        start_group backend "$BACKEND" ./gradlew --no-daemon --quiet --console=plain bootRun
        info "starting backend (logs/backend.log)..."
        wait_for backend backend_up 180 && ok "backend up ($BACKEND_URL)" || rc=1
    fi
    if frontend_up; then ok "frontend already up ($FRONTEND_URL)"
    else
        frontend_deps_ok || { fail "frontend: node_modules missing - run: (cd frontend && npm ci)"; return 3; }
        start_group frontend "$FRONTEND" npm run dev
        wait_for frontend frontend_up 60 && ok "frontend up ($FRONTEND_URL)" || rc=1
    fi
    return $rc
}

down() {
    local n
    for n in frontend backend; do
        if alive "$n"; then
            local pg; pg="$(cat "$(pidfile "$n")")"
            kill -TERM -- -"$pg" 2>/dev/null
            for _ in {1..20}; do pgrep -g "$pg" >/dev/null || break; sleep 0.5; done
            pgrep -g "$pg" >/dev/null && kill -KILL -- -"$pg" 2>/dev/null
            ok "stopped $n"
        else
            info "$n: not started by tools/dev.sh"
        fi
        rm -f "$(pidfile "$n")"
    done
}

status() {
    backend_up && ok "backend up ($BACKEND_URL)" || info "backend down"
    frontend_up && ok "frontend up ($FRONTEND_URL)" || info "frontend down"
    docker_ok && info "postgres: $(cd "$ROOT" && docker compose ps --format '{{.Service}} {{.State}}' 2>/dev/null | tr '\n' ' ')"
    return 0
}

case "${1:-up}" in
    up) up ;;
    down) down ;;
    status) status ;;
    logs) tail -n 100 -f "$RUN_DIR/${2:-backend}.log" ;;
    -h|--help) sed -n '2,8p' "$0" ;;
    *) echo "unknown command: $1"; exit 3 ;;
esac
