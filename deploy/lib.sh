#!/usr/bin/env bash
# Shared by the other scripts in this directory. Source it, don't run it.

ROOT="${ROOTDIR:-/srv/roomfinder}"

log() { printf '[%s] %s\n' "$(date -u +%FT%TZ)" "$*"; }
die() { log "ERROR: $*" >&2; exit 1; }

# docker compose against the files on the server. .deploy wins over .env if a key is in both.
compose() {
  docker compose -f "$ROOT/docker-compose.prod.yml" \
    --env-file "$ROOT/.env" --env-file "$ROOT/.deploy" "$@"
}

# Loads .env into this shell's environment (for scripts that need the backup keys etc.).
# .env is read as shell here, so keep values simple: KEY=value, no spaces around '=', no quotes needed.
load_env() {
  [ -f "$ROOT/.env" ] || die "$ROOT/.env is missing - see docs/ops/deployment.md, first-time setup"
  set -a
  # shellcheck disable=SC1091
  . "$ROOT/.env"
  set +a
}
