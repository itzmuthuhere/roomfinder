#!/usr/bin/env bash
# Nightly backup, run by roomfinder-backup.timer: a pg_dump of the database plus an incremental copy
# of uploaded media, both to Cloudflare R2, with 30-day retention on the dumps.
#
# Media is *copied*, never synced: if files ever disappear from the VM (a bad rm, a failed disk),
# the next run must not faithfully delete the only other copy. Dumps are timestamped and never
# overwritten. Requires BACKUP_R2_* and ENVIRONMENT in /srv/roomfinder/.env.
set -euo pipefail
. "$(dirname "$0")/lib.sh"

load_env
: "${ENVIRONMENT:?ENVIRONMENT (staging|production) is required in .env}"
: "${BACKUP_R2_ACCOUNT_ID:?}" "${BACKUP_R2_ACCESS_KEY_ID:?}" "${BACKUP_R2_SECRET_ACCESS_KEY:?}" "${BACKUP_R2_BUCKET:?}"
MEDIA_DIR="${MEDIA_DIR:-/srv/roomfinder/media}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
RCLONE_IMAGE="${RCLONE_IMAGE:-rclone/rclone:latest}"

WORK="$(mktemp -d "$ROOT/backup-work.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

# rclone gets its config from these variables; passing only the names to `docker run -e` keeps
# the secret values out of the process list.
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
    -v "$WORK:/work" -v "$MEDIA_DIR:/media:ro" \
    "$RCLONE_IMAGE" "$@"
}

STAMP="$(date -u +%Y%m%d-%H%M%S)"
DUMP="$WORK/roomfinder-$STAMP.dump"
REMOTE="r2:${BACKUP_R2_BUCKET}/${ENVIRONMENT}"

log "dumping database"
compose exec -T db pg_dump -U roomfinder -Fc roomfinder > "$DUMP"
[ -s "$DUMP" ] || die "pg_dump produced an empty file"

# A dump that can't even be listed is not a backup.
compose exec -T db pg_restore --list < "$DUMP" > /dev/null || die "pg_restore cannot read the dump"
log "dump ok ($(du -h "$DUMP" | cut -f1))"

log "uploading dump"
rclone copyto "/work/$(basename "$DUMP")" "$REMOTE/db/$(basename "$DUMP")"

if [ -d "$MEDIA_DIR" ]; then
  log "copying media"
  rclone copy /media "$REMOTE/media"
fi

log "removing dumps older than ${RETENTION_DAYS} days"
rclone delete --min-age "${RETENTION_DAYS}d" "$REMOTE/db"

# Optional dead-man's switch (e.g. a healthchecks.io URL): a backup that silently stops running
# is the failure to worry about, and this is what tells you.
if [ -n "${BACKUP_PING_URL:-}" ]; then
  curl -fsS --max-time 10 --retry 3 "$BACKUP_PING_URL" > /dev/null || log "WARNING: backup ping failed"
fi

log "backup complete: $(basename "$DUMP")"
