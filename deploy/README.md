# Production deployment

One EU VPS running `deploy/compose.yml` (ADR-0006): **Caddy** (HTTPS, the only public service) -> **app**
(Spring Boot jar with the built frontend inside) -> **Postgres** (PostGIS + pgvector) + **Garage** (S3, photos).

## First setup

1. VPS with Docker + compose plugin; DNS `A`/`AAAA` record of the domain pointing at it; ports 80 and 443 open.
2. Mount the big volume, e.g. at `/mnt/photos` (Garage data lives there, see `PHOTOS_DIR`).
3. Get the code onto the server (e.g. `git clone` to `/opt/fotozielnik`).
4. `cp deploy/.env.example deploy/.env`, fill in every value (`openssl rand -hex 32` for secrets),
   `chmod 600 deploy/.env`.
5. `cd deploy && docker compose up -d --build`. First build takes a few minutes.
6. Open `https://<domain>`, sign in as `ADMIN_EMAIL` with `ADMIN_INITIAL_PASSWORD`, set a new password,
   create the other users under Admin.

## Deploy a new version

```bash
cd /opt/fotozielnik && git pull && cd deploy && docker compose up -d --build
```

Flyway migrates the database on start. Downtime is the app restart (< 1 min); background jobs that were
running are picked up again after their lease expires (ADR-0009).

## Logs and health

```bash
docker compose ps                 # all services should be "healthy"
docker compose logs -f app        # application log
```

`/actuator` is not reachable from outside (Caddy returns 404); the container healthcheck uses it internally.

## Backups (NFR-010)

`backup.sh` dumps the database and syncs the photo bucket to an off-site **rclone** remote (any S3 provider,
a Hetzner Storage Box via SFTP, ...), keeping `BACKUP_KEEP_DAYS` (14) days.

1. Install rclone on the server and run `rclone config` to create a remote (e.g. `offsite`), then set
   `BACKUP_RCLONE_REMOTE=offsite:fotozielnik-backup` and `RCLONE_CONFIG_DIR` in `deploy/.env`.
2. Try it once by hand: `deploy/backup.sh`.
3. Schedule it: `crontab -e` -> `15 3 * * * /opt/fotozielnik/deploy/backup.sh >> /var/log/fotozielnik-backup.log 2>&1`

## Restore

Practise this once before M1 is done (roadmap exit criterion).

1. Photos: `docker compose --profile backup run --rm rclone copy <remote>/photos garage:photos`
2. Database: copy a dump into `deploy/backups/` and run `deploy/restore.sh backups/db-<stamp>.dump`
   (asks for confirmation, stops the app, restores, starts the app).

## Testing the stack locally

Use another project name, high ports and a throwaway env file, so nothing touches the dev stack:

```bash
cd deploy
cat > /tmp/fz-prod.env <<EOF
DOMAIN=localhost
HTTP_PORT=18080
HTTPS_PORT=18443
POSTGRES_PASSWORD=$(openssl rand -hex 32)
GARAGE_RPC_SECRET=$(openssl rand -hex 32)
GARAGE_ACCESS_KEY_ID=GK$(openssl rand -hex 12)
GARAGE_SECRET_ACCESS_KEY=$(openssl rand -hex 32)
ADMIN_EMAIL=admin@prodtest.local
ADMIN_INITIAL_PASSWORD=$(openssl rand -hex 8)
EOF
docker compose -p fzprodtest --env-file /tmp/fz-prod.env up -d --build
# https://localhost:18443 (self-signed: accept the warning) ... then:
docker compose -p fzprodtest --env-file /tmp/fz-prod.env down
docker volume rm fzprodtest_pgdata fzprodtest_garage fzprodtest_caddy-data fzprodtest_caddy-config
```
