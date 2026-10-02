# Environments

| Environment | URL | Database | Purpose |
| --- | --- | --- | --- |
| local | localhost:8080 (API), localhost:5173 (web) | Docker Postgres+PostGIS | Development |
| staging | staging.<domain> | Postgres+PostGIS on its own VM, nightly backup to R2 | Every merge to `main` lands here; owner demos, testing on a real phone |
| production | app.<domain> | Postgres+PostGIS on its own VM, nightly backup to R2 | Live; only a sha already run on staging is promoted |

Each VM runs the same stack (see [Deployment](deployment.md)); the only differences are the values
in its own `/srv/roomfinder/.env`, notably `ENVIRONMENT`, `DOMAIN` and `OTP_PROVIDER` (staging may
use `log`, readable in the API logs; production must be `msg91` before any real user logs in).

Every environment runs the same migrations. Production data is never copied to local; use seed scripts.
