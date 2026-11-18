#!/usr/bin/env bash
# "Look at it": opens a page of the running app in headless Chromium, saves a screenshot to
# captures/<name>.png and reports console errors, page errors and HTTP >= 400 responses.
# Starts the stack (tools/dev.sh up) if it is not running and stops it again afterwards (unless --keep).
# Agents: Read the PNG afterwards - you can see it.
#
# Usage: tools/look.sh [<path or url>] [--name <shot>] [--wait <css>] [--eval '<js body>'|--eval-file f.js]
#                      [--width 1280] [--height 800] [--mobile] [--full] [--keep]
#   path     default "/"; relative paths go to the frontend (http://localhost:5173)
#   --eval   body of an async function run in the page, e.g. --eval 'return document.title'
#   --mobile 390x844 viewport
set -uo pipefail
source "$(dirname "$0")/_lib.sh"

path="/" name="" wait="" evalfile="" width=1280 height=800 full=0 keep=0
while (( $# )); do
    case "$1" in
        --name) name="$2"; shift ;;
        --wait) wait="$2"; shift ;;
        --eval) evalfile="$(mktemp --suffix=.js)"; printf '%s\n' "$2" >"$evalfile"; shift ;;
        --eval-file) evalfile="$(realpath "$2")"; shift ;;
        --width) width="$2"; shift ;;
        --height) height="$2"; shift ;;
        --mobile) width=390 height=844 ;;
        --full) full=1 ;;
        --keep) keep=1 ;;
        -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
        -*) echo "unknown option: $1"; exit 3 ;;
        *) path="$1" ;;
    esac
    shift
done
[[ "$path" == http* ]] && url="$path" || url="$FRONTEND_URL/${path#/}"
[[ -n "$name" ]] || name="$(sed -E 's#^https?://[^/]+##; s#[^A-Za-z0-9]+#-#g; s#^-+|-+$##g' <<<"$path")"
[[ -n "$name" ]] || name="home"
frontend_deps_ok || { fail "node_modules missing - run: (cd frontend && npm ci)"; exit 3; }

started=0
if ! backend_up || ! frontend_up; then
    "$ROOT/tools/dev.sh" up || exit $?
    started=1
fi

mkdir -p "$ROOT/captures"
out="$ROOT/captures/$name.png"
args=(--url "$url" --out "$out" --width "$width" --height "$height")
[[ -n "$wait" ]] && args+=(--wait "$wait")
[[ -n "$evalfile" ]] && args+=(--eval-file "$evalfile")
(( full )) && args+=(--full)
(cd "$FRONTEND" && node e2e/look.mjs "${args[@]}")
rc=$?

if (( started && ! keep )); then "$ROOT/tools/dev.sh" down >/dev/null; info "stopped the dev servers this run started (--keep leaves them up)"; fi
(( rc == 0 )) && ok "look: captures/$name.png" || fail "look: problems above (screenshot, if taken: captures/$name.png)"
exit $rc
