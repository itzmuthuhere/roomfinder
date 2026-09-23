# RoomFinder — local setup

One-time prerequisites: Java 21 (SDKMAN), Node 20+, Docker Desktop, git.

## 1. Database (2 minutes)

```bash
docker compose up -d db          # or: make db
docker compose ps                # db should be "healthy"
make psql                        # \dt shows nothing yet — correct
```

Postgres with PostGIS runs on localhost:5432, user `roomfinder`, password `localdev`, db `roomfinder`.
Data survives restarts in a Docker volume. `make db-reset` wipes it.

## 2. Backend (15 minutes)

Generate the project once, from the repo root:

```bash
curl https://start.spring.io/starter.zip \
  -d type=gradle-project -d language=java -d javaVersion=21 \
  -d bootVersion=3.3.4 \
  -d groupId=com.roomfinder -d artifactId=api -d name=api \
  -d packageName=com.roomfinder \
  -d dependencies=web,data-jpa,validation,flyway,postgresql,actuator,testcontainers \
  -o api.zip && unzip -q api.zip -d api && rm api.zip
```

Then:

1. Copy `application.yml` from this kit into `api/src/main/resources/` (replaces the empty `application.properties` — delete that file).
2. Copy `V1__init.sql` into `api/src/main/resources/db/migration/`.
3. Add springdoc to `api/build.gradle` dependencies:
   `implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'`
4. Run it:

```bash
make api        # or: cd api && ./gradlew bootRun
```

Verify:

```bash
curl localhost:8080/actuator/health     # {"status":"UP"}
open http://localhost:8080/swagger      # empty API list, that's fine
make psql -> \dt                        # all tables now exist, created by Flyway
```

If Flyway fails, the app refuses to start. That is intentional: never let the schema drift.

## 3. Frontend (10 minutes)

```bash
npm create vite@latest app -- --template react-ts
cd app && npm install
npm install -D tailwindcss @tailwindcss/vite
```

1. Copy `vite.config.ts` from this kit over the generated one (it proxies `/api` to :8080, so no CORS setup in dev).
2. Start it:

```bash
make web        # or: cd app && npm run dev
```

Open http://localhost:5173.

## 4. Prove the three parts talk to each other

Write one endpoint and one screen before anything else:

- `GET /api/ping` in a `HealthController` returning `{"ok":true,"beds":<count from db>}`
- A React page that fetches `/api/ping` and renders the count

When the browser shows a number that came out of Postgres, your stack is real. Everything after that is features.

## 5. Daily routine

```bash
make db      # once, stays running
make api     # terminal 1
make web     # terminal 2
make test    # before every commit
```

## Environment variables

Copy `.env.example` to `.env` and fill it. `.env` is gitignored and must never be committed.
Local dev works with no secrets at all until you add SMS OTP, WhatsApp or media upload.
