# Deploy pipeline

- **Shipped:** 2026-10-02
- **Status:** partial — written and statically checked, never run against a real VM or registry
- **Related:** [Deployment](../ops/deployment.md) holds the full how-to; this note records what was built and what wasn't verified.

## Why

The app is feature-complete for launch but nothing could be deployed: no images, no server layout,
no pipeline, no backups. This adds all of it for one-VM-per-environment, matching CLAUDE.md's stack
(single VM in an Indian region, Postgres on the same host, nightly backup to R2, GitHub Actions).

## How it works

Merge to `main` → tests → two images (api, web) tagged with the commit sha → staging. Production is
promoted by hand with a sha that already ran on staging; rollback is the same action with an older
sha. On the VM, `deploy.sh` restarts the stack, waits for the API health check, and restores the
last good release if it never turns healthy. Caddy serves the web build, proxies `/api` and `/media`,
and handles HTTPS, which also fixes the SPA-routing gap noted in the search feature (deep links like
`/pgs/7` now fall back to the app). CI holds SSH access only; application secrets exist solely in
`/srv/roomfinder/.env` on each VM.

## API

None.

## Data

None. No migration.

## Screens

None.

## Background jobs

On the VM, not in the app: `roomfinder-backup.timer` (nightly 02:30 IST, pg_dump + media copy to R2,
30-day retention) and `roomfinder-restore-test.timer` (monthly proof the newest backup restores).

## Config

New server-only keys, names in `.env.example`: `ENVIRONMENT`, `DOMAIN`, `BACKUP_R2_ACCOUNT_ID`,
`BACKUP_R2_ACCESS_KEY_ID`, `BACKUP_R2_SECRET_ACCESS_KEY`, `BACKUP_R2_BUCKET`, `BACKUP_PING_URL`.
GitHub Environment secrets per environment: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`,
`DEPLOY_KNOWN_HOSTS`. `api/build.gradle` now names the jar `roomfinder-api.jar` so the Dockerfile
doesn't depend on the version suffix.

## Tests

No application tests apply. Checked statically: all three workflows pass `actionlint`; the compose
file parses and refuses to start if `AUTH_JWT_SECRET` (or `DB_PASSWORD`, `OTP_PROVIDER`, `DOMAIN`)
is missing; the Caddyfile validates and was run against a stub backend (deep links fall back to the
app, `/api` and `/media` proxy, `/actuator` and `/swagger` are not reachable, cache headers
correct); shell scripts pass `bash -n`; `npm ci` succeeds on the lockfile. CI also re-runs the
compose, Caddyfile and shell checks on every PR.

## Known gaps

- **Never run for real.** No Docker daemon was available while writing this, so neither image has
  been built, no script has run on a VM, and the backup upload, restore test and systemd timers are
  untested. `bootstrap-vm.sh` and `deploy.sh` are most likely to need a small fix on first use.
- Restart is stop-then-start, not zero-downtime (see Deployment).
- `docs.yml` (the docs-site workflow) calls Gradle tasks `build.gradle` doesn't define, so it
  fails today; unrelated to this change, not fixed here.
- No monitoring or alerting beyond the optional backup heartbeat; Sentry (named in CLAUDE.md) is not
  wired into the API.
- Media still lives on the VM's disk (backed up to R2, not served from it). Moving to presigned R2
  uploads, as CLAUDE.md intends, is a later change behind the existing `MediaStorage` interface.
