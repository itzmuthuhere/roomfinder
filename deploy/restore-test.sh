#!/usr/bin/env bash
# Monthly (roomfinder-restore-test.timer) proof that the newest backup actually restores: downloads
# it from R2, restores it into a throwaway Postgres+PostGIS container that is not the live database,
# and checks the schema history is there. Nothing here touches the running stack.
set -euo pipefail
. "$(dirname "$0")/lib.sh"

load_env
: "${ENVIRONMENT:?}" "${BACKUP_R2_ACCOUNT_ID:?}" "${BACKUP_R2_ACCESS_KEY_ID:?}" "${BACKUP_R2_SECRET_ACCESS_KEY:?}" "${BACKUP_R2_BUCKET:?}"
RCLONE_IMAGE="${RCLONE_IMAGE:-rclone/rclone:latest}"
SCRATCH="rf-restore-test-$$"

WORK="$(mktemp -d "$ROOT/restore-work.XXXXXX")"
cleanup() { docker rm -f "$SCRATCH" > /dev/null 2>&1 || true; rm -rf "$WORK"; }
trap cleanup EXIT

export RCLONE_CONFIG_R2_TYPE=s3
export RCLONE_CONFIG_R2_PROVIDER=Cloudflare
export RCLONE_CONFIG_R2_ACCESS_KEY_ID="$BACKUP_R2_ACCESS_KEY_ID"
export RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$BACKUP_R2_SECRET_ACCESS_KEY"
export RCLONE_CONFIG_R2_ENDPOINT="https://${BACKUP_R2_ACCOUNT_ID}.r2.cloudflarestorage.com"
export RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true

rclone() {
  docker run --rm --user "$(id -u):$(id -g)" \
    -e RCLONE_CONFIG_R2_TYPE -e RCLONE_CONFIG_R2_PROVIDER \
    -e RCLONE_CONFIG_R2_ACCESS_KEY_ID -e RCLONE_CONFIG_R2_SECRET_ACCESS_KEY \
    -e RCLONE_CONFIG_R2_ENDPOINT -e RCLONE_CONFIG_R2_NO_CHECK_BUCKET \
    -v "$WORK:/work" "$RCLONE_IMAGE" "$@"
}

REMOTE="r2:${BACKUP_R2_BUCKET}/${ENVIRONMENT}/db"
LATEST="$(rclone lsf --files-only "$REMOTE" | sort | tail -n 1)"
[ -n "$LATEST" ] || die "no backups found under $REMOTE"
log "newest backup: $LATEST"

rclone copyto "$REMOTE/$LATEST" "/work/restore.dump"

log "starting scratch postgres"
docker run -d --name "$SCRATCH" \
  -e POSTGRES_USER=roomfinder -e POSTGRES_PASSWORD=restore-test -e POSTGRES_DB=postgres \
  postgis/postgis:16-3.4 > /dev/null
until docker exec "$SCRATCH" pg_isready -U roomfinder > /dev/null 2>&1; do sleep 2; done

# Restore into an empty database (template0, no PostGIS preinstalled) so the dump's own
# CREATE EXTENSION statements run cleanly instead of colliding with the image's defaults.
docker exec "$SCRATCH" createdb -U roomfinder -T template0 restore_check
docker cp "$WORK/restore.dump" "$SCRATCH:/tmp/restore.dump"
docker exec "$SCRATCH" pg_restore -U roomfinder -d restore_check --no-owner /tmp/restore.dump

MIGRATIONS="$(docker exec "$SCRATCH" psql -U roomfinder -d restore_check -tAc 'select count(*) from flyway_schema_history')"
USERS="$(docker exec "$SCRATCH" psql -U roomfinder -d restore_check -tAc 'select count(*) from app_user')"
BEDS="$(docker exec "$SCRATCH" psql -U roomfinder -d restore_check -tAc 'select count(*) from bed')"
[ "${MIGRATIONS:-0}" -gt 0 ] || die "restored database has no flyway history - the backup is not usable"

log "restore OK: $MIGRATIONS migrations, $USERS users, $BEDS beds (from $LATEST)"
