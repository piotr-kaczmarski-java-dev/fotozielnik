#!/usr/bin/env bash
# Nightly backup (NFR-010): database dump + photo objects, copied off-site, kept BACKUP_KEEP_DAYS days.
# Run on the server from cron, e.g.:  15 3 * * *  /opt/fotozielnik/deploy/backup.sh >> /var/log/fotozielnik-backup.log 2>&1
#
# Needs: this stack running, and an rclone remote (BACKUP_RCLONE_REMOTE in .env) configured in RCLONE_CONFIG_DIR.
# rclone runs as the compose service `rclone` (profile "backup"), inside the network where Garage is reachable.
set -euo pipefail
cd "$(dirname "$0")"
set -a; source .env; set +a
: "${BACKUP_RCLONE_REMOTE:?set BACKUP_RCLONE_REMOTE in deploy/.env}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
STAMP="$(date -u +%Y-%m-%dT%H%MZ)"
rclone() { docker compose --profile backup run --rm -T -v "$PWD/backups:/backups" rclone "$@"; }
mkdir -p backups

echo "[$STAMP] database dump"
docker compose exec -T postgres pg_dump -U fotozielnik -d fotozielnik --format=custom --compress=6 \
    > "backups/db-$STAMP.dump.part"
mv "backups/db-$STAMP.dump.part" "backups/db-$STAMP.dump"
find backups -name 'db-*.dump' -mtime +"$KEEP_DAYS" -delete

echo "[$STAMP] copy dumps off-site"
rclone copy /backups "$BACKUP_RCLONE_REMOTE/db" --include 'db-*.dump'
rclone delete "$BACKUP_RCLONE_REMOTE/db" --min-age "${KEEP_DAYS}d"

echo "[$STAMP] sync photos off-site"
# Originals are content-addressed and never change, so a sync is cheap after the first run. Objects deleted
# here are moved to a dated folder off-site (--backup-dir) and kept KEEP_DAYS, so a mistake can be undone.
rclone sync garage:photos "$BACKUP_RCLONE_REMOTE/photos" \
    --backup-dir "$BACKUP_RCLONE_REMOTE/photos-deleted/$STAMP" --fast-list --transfers 8
rclone delete "$BACKUP_RCLONE_REMOTE/photos-deleted" --min-age "${KEEP_DAYS}d" --rmdirs || true

echo "[$STAMP] done"
