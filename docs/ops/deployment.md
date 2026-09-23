# Deployment

## Backend
1. Merge to `main` → GitHub Actions builds the Spring Boot jar and runs tests against a Postgres service container.
2. Image is built and pushed, then the VM pulls and restarts via systemd (or docker compose).
3. Flyway runs on startup. A failed migration aborts the boot; the previous container keeps serving.

## Frontend
1. Same pipeline builds the React app (`app/`) and uploads static assets to R2/Pages with a hashed filename per build.
2. The mobile wrapper is rebuilt only when native config changes; web content updates without a store release.

## Docs
Pushed on every merge that touches `docs/`, `mkdocs.yml` or `api/`, published to GitHub Pages.

## Rollback
Redeploy the previous image tag. Never roll a migration back in production; write a forward migration.
