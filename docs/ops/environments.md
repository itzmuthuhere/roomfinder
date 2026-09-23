# Environments

| Environment | URL | Database | Purpose |
| --- | --- | --- | --- |
| local | localhost:8080 | Docker Postgres+PostGIS | Development |
| staging | staging.<domain> | Small managed instance | Owner demos, testing on a real phone |
| production | api.<domain> | VM Postgres, nightly backup to R2 | Live |

Every environment runs the same migrations. Production data is never copied to local; use seed scripts.
