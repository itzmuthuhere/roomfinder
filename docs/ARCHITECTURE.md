# Architecture — current state

Written for a coding session starting cold. Update this file whenever a feature lands.

## Shape

One Spring Boot modular monolith (`api/`) plus one React PWA (`app/`), one Postgres+PostGIS
database. No microservices, no queue broker, no ORM entities.

```
roomfinder/
├── CLAUDE.md              build rules (read first)
├── BACKLOG.md             what to build next, in order
├── docs/                  MkDocs site: product, features, ADRs, ops, generated API + schema
├── api/                   Spring Boot 3.3, Java 21, Gradle
│   └── src/main/java/com/roomfinder/
│       ├── common/        ApiException, ApiExceptionHandler
│       ├── bed/           BedState machine, BedService, BedRepository, BedController
│       ├── pg/            PG onboarding: rooms, beds, listing status
│       ├── media/         photo + video upload, local disk storage, MP4 metadata scrub
│       ├── dev/           TEMPORARY: creates owners without auth. Delete when auth lands
│       └── health/        /api/ping, proves the stack end to end
└── app/src/
    ├── lib/api.ts         fetch wrapper, types, owner id from localStorage (temporary)
    ├── lib/upload.ts      XHR upload with progress, canvas downscale, EXIF drop
    └── owner/             OwnerPage, BedGrid, MediaManager
```

## Data model (V1 + V2 migrations)

`app_user` → `pg_property` → `room` → `bed`, with `bed_state_log` as the audit trail.
`booking`, `tenancy`, `rent_payment` exist in V1 but are not used yet.
`media` holds photos and one video per PG, with `is_cover` and partial unique indexes.

Location is `geography(point,4326)`, written with `ST_MakePoint(lng, lat)` — longitude first.

## The core invariant

A bed is a row with a state: `VACANT`, `HELD`, `BOOKED`, `OCCUPIED`, `ON_NOTICE`, `BLOCKED`.

Transitions are declared in `BedState` and nothing else may change a bed. Every change goes
through a conditional update:

```sql
update bed set state = :next where id = :id and state = :expected
```

Zero rows updated means someone else won the race → 409 `BED_STATE_CHANGED`. There is no
same-state shortcut on purpose: a second seeker asking to hold an already-held bed must be told
no, not quietly told yes. `BedHoldConcurrencyTest` (50 threads, one winner) pins this and must
never be deleted or weakened.

## Conventions

- Repositories use Spring `JdbcClient` with explicit SQL, not JPA entities (ADR-005).
- Controllers are thin; business rules live in services; DTOs are records, never entities.
- Money in whole rupees as `int` today; never floating point.
- Errors are `{code, message}` with real HTTP statuses, from one `@ControllerAdvice`.
- Schema changes are new Flyway migrations. Never edit an applied one.
- Tests use Testcontainers against real PostGIS. No H2. `AbstractDbTest` starts one container
  per JVM and sets the datasource explicitly.
- Frontend: plain fetch wrapper, no Redux, mobile-first, every screen has empty/loading/error states.

## What works today

Owner creates a PG → adds rooms (beds are created automatically) → sees a colour-coded bed grid
→ changes bed states with audit logging → uploads room photos and one walkthrough video →
toggles the listing between DRAFT and LIVE.

## What does not exist yet

**Authentication.** The owner id travels in an `X-Owner-Id` header and `/api/dev/owners` creates
owners with no login. Nothing is deployable until this is replaced. No endpoint currently checks
that a PG belongs to the caller.

Also missing: seeker search, visit booking with holds and expiry, rent register, notices,
reviews, the Capacitor mobile wrapper, and any deployment.

## Gotchas discovered so far

- Tests must not inherit `spring.datasource.url` from `application.yml`, or they silently run
  against the dev database.
- MP4 metadata is wiped in place (box renamed to `free`, payload zeroed), never spliced out:
  removing bytes from `moov` invalidates the absolute chunk offsets in a faststart file and the
  video stops playing.
- File deletes happen after the transaction commits, never inside it.
- Git Bash gives Node CLIs no TTY; run Claude Code from PowerShell.
