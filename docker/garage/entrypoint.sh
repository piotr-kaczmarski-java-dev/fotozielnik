#!/bin/sh
# Starts Garage and makes sure the single-node layout, access key and bucket exist (idempotent).
# Required env: GARAGE_RPC_SECRET (64 hex), GARAGE_ACCESS_KEY_ID (GK + 24 hex), GARAGE_SECRET_ACCESS_KEY (64 hex).
# Optional: GARAGE_BUCKET (default photos), GARAGE_CAPACITY (default 100G; only used for data placement).
set -eu
: "${GARAGE_RPC_SECRET:?must be set}" "${GARAGE_ACCESS_KEY_ID:?must be set}" "${GARAGE_SECRET_ACCESS_KEY:?must be set}"
BUCKET="${GARAGE_BUCKET:-photos}"
rm -f /tmp/garage-ready

cat > /etc/garage.toml <<CONF
metadata_dir = "/var/lib/garage/meta"
data_dir = "/var/lib/garage/data"
db_engine = "sqlite"
replication_factor = 1
rpc_bind_addr = "[::]:3901"
rpc_public_addr = "127.0.0.1:3901"
rpc_secret = "${GARAGE_RPC_SECRET}"

[s3_api]
s3_region = "garage"
api_bind_addr = "[::]:3900"
root_domain = ".s3.garage.localhost"
CONF

garage server &
pid=$!
trap 'kill -TERM "$pid" 2>/dev/null; wait "$pid"' TERM INT

until garage status >/dev/null 2>&1; do
    kill -0 "$pid" 2>/dev/null || { echo "garage server exited" >&2; exit 1; }
    sleep 0.5
done

if garage status | grep -q 'NO ROLE ASSIGNED'; then
    node=$(garage node id -q | cut -d@ -f1)
    garage layout assign -z dc1 -c "${GARAGE_CAPACITY:-100G}" "$node"
    garage layout apply --version "$(( $(garage layout show | sed -n 's/^Current cluster layout version: //p') + 1 ))"
fi
garage key info "$GARAGE_ACCESS_KEY_ID" >/dev/null 2>&1 \
    || garage key import --yes -n app "$GARAGE_ACCESS_KEY_ID" "$GARAGE_SECRET_ACCESS_KEY"
garage bucket info "$BUCKET" >/dev/null 2>&1 || garage bucket create "$BUCKET"
garage bucket allow --read --write --owner "$BUCKET" --key "$GARAGE_ACCESS_KEY_ID" >/dev/null

touch /tmp/garage-ready
echo "garage ready: bucket '$BUCKET'"
wait "$pid"
