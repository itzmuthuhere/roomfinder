# Local setup (Windows)

Record of the development environment as it was first brought up, and the errors seen on the way.

## Verified working

| Component | Version | Notes |
| --- | --- | --- |
| OS / shell | Windows 11, Git Bash (MINGW64) | `make` is not available; run the raw commands below |
| Java | 21.0.5 (`C:\Program Files\Java\jdk-21`) | Matches the Gradle toolchain in `api/build.gradle` |
| Gradle | 8.10.2 via wrapper | First run downloads the distribution, ~6 minutes; later runs are seconds |
| Spring Boot | 3.3.4 | Boots, Tomcat on 8080 |
| Postgres | postgis/postgis:16-3.4 via Docker Compose | Must be running before the API starts |

## Start order (this order matters)

```bash
# 1. Docker Desktop must be running first (whale icon steady, "Engine running")
docker ps                       # empty table = engine reachable

# 2. Database
cd ~/projects/roomfinder
docker compose up -d db
docker compose ps               # roomfinder-db must show healthy

# 3. Backend (Flyway applies V1__init on boot)
cd api && ./gradlew.bat bootRun

# 4. Frontend, separate terminal
cd ~/projects/roomfinder/app && npm install && npm run dev
```

Verify:

```bash
curl localhost:8080/actuator/health                                   # {"status":"UP"}
curl localhost:8080/api/ping                                          # {"ok":true,"pgs":0,"beds":0}
docker exec -it roomfinder-db psql -U roomfinder -d roomfinder -c '\dt'   # 11 tables
```

Then open http://localhost:5173 — green text means browser → Vite proxy → Spring Boot → Postgres is working end to end.

## Errors seen, and what they mean

| Error | Cause | Fix |
| --- | --- | --- |
| `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified` | Docker Desktop not running | Start Docker Desktop, wait for "Engine running" |
| `Connection to localhost:5432 refused` during Flyway, app exits | Database container not up | Step 2 above. The app is *designed* to refuse to start without a database — never disable Flyway to work around it |
| `bash: ./gradlew.bat: No such file or directory` | Backend project not generated yet | Resolved: the Gradle wrapper and build files now live in `api/` |
| `bash: cd: app: No such file or directory` | Frontend not scaffolded yet | Resolved: `app/` now contains package.json, index.html and src/ |
| `Picked up JAVA_TOOL_OPTIONS: -Djava.io.tmpdir=D:\tmp_sock` | A pre-existing environment variable on this machine | Harmless. Remove from Windows env vars if `D:` is ever unavailable |

## Fallback if Docker cannot be used

Install PostgreSQL 16 natively (EDB installer) and add PostGIS through Stack Builder, create database `roomfinder` and user `roomfinder`, then set `DB_URL`, `DB_USER`, `DB_PASSWORD` in `.env`. Nothing in the application changes.

## Notes

- Line endings are pinned by `.gitattributes` (`gradlew` LF, `.bat` CRLF). Do not commit with `core.autocrlf true`.
- `.env` is never committed. `.env.example` lists key names only.
- The first `gradlew.bat` run needs internet for the Gradle distribution and Maven Central.
