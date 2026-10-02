#!/usr/bin/env bash
# Usage: deploy.sh <image-repo> <image-tag>
# Run on the VM by the deploy workflow (or by hand for a rollback). Pulls the given images,
# restarts the stack, waits for the API to report healthy, and puts the last good tag back if it
# doesn't. Note this is a stop-then-start restart, not a zero-downtime swap: expect the site to be
# unavailable for the seconds the API takes to boot, and for the full wait if a release fails.
set -euo pipefail
. "$(dirname "$0")/lib.sh"

REPO="${1:?usage: deploy.sh <image-repo> <image-tag>}"
TAG="${2:?usage: deploy.sh <image-repo> <image-tag>}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"

cd "$ROOT"
[ -f .env ] || die ".env is missing - see docs/ops/deployment.md, first-time setup"

printf 'IMAGE_REPO=%s\nIMAGE_TAG=%s\n' "$REPO" "$TAG" > .deploy
log "deploying $REPO @ $TAG"

compose pull api web
compose up -d --remove-orphans

api_health() {
  local id
  id="$(compose ps -q api)"
  [ -n "$id" ] || { echo missing; return; }
  docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$id"
}

waited=0
until [ "$(api_health)" = "healthy" ]; do
  if [ "$waited" -ge "$HEALTH_TIMEOUT" ]; then
    log "api did not become healthy within ${HEALTH_TIMEOUT}s - last logs:"
    compose logs --tail 80 api || true
    if [ -f .deploy.good ]; then
      log "rolling back to $(tr '\n' ' ' < .deploy.good)"
      cp .deploy.good .deploy
      compose up -d --remove-orphans
      die "release $TAG failed; previous release restored (check 'compose logs api' for why)"
    fi
    die "release $TAG failed and there is no previous good release to roll back to"
  fi
  sleep 5
  waited=$((waited + 5))
done

cp .deploy .deploy.good
log "api healthy after ~${waited}s"

# The api being healthy doesn't prove the proxy in front of it works (DNS, TLS, Caddy routing),
# so check that path too. Warn rather than roll back: a rollback can't fix DNS or a certificate.
load_env
if [ -n "${DOMAIN:-}" ]; then
  if curl -fsS --max-time 10 --retry 6 --retry-delay 5 --retry-connrefused \
       --resolve "${DOMAIN}:443:127.0.0.1" "https://${DOMAIN}/api/ping" > /dev/null; then
    log "smoke check passed: https://${DOMAIN}/api/ping"
  else
    log "WARNING: api is healthy but https://${DOMAIN}/api/ping failed - check DNS, ports 80/443 and 'compose logs web'"
  fi
fi

docker image prune -f > /dev/null
log "done: $TAG is live"
