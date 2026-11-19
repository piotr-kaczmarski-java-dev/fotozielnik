#!/usr/bin/env bash
# Restores a database dump made by backup.sh (NFR-010: test this once before M1 ends).
#   deploy/restore.sh backups/db-2027-01-31T0315Z.dump
# Photos: copy them back into the bucket first (README.md, "Restore").
# DESTRUCTIVE: replaces the current database. The app is stopped during the restore.
set -euo pipefail
cd "$(dirname "$0")"
DUMP="${1:?usage: restore.sh backups/db-<stamp>.dump}"
[[ -f "$DUMP" ]] || { echo "no such file: $DUMP" >&2; exit 1; }
read -r -p "Replace the current database with $DUMP? Type 'restore' to continue: " answer
[[ "$answer" == "restore" ]] || { echo "aborted"; exit 1; }

docker compose stop app
docker compose exec -T postgres pg_restore -U fotozielnik -d fotozielnik --clean --if-exists --no-owner < "$DUMP"
docker compose start app
echo "restored $DUMP"
