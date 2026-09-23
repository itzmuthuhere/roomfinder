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
│       ├── auth/          Phone OTP login, JWT issue/parse, JwtAuthFilter, ownership checks
│       ├── bed/           BedState machine, BedService, BedRepository, BedController
│       ├── pg/            PG onboarding: rooms, beds, listing status
│       ├── media/         photo + video upload, local disk storage, MP4 metadata scrub
│       ├── search/        GET /api/search: PostGIS radius + filters over LIVE, vacant-bed PGs
│       ├── booking/       Visit booking: holds a bed, owner accept/decline, HoldExpiryJob sweep
│       ├── notify/        WhatsAppSender interface; LoggingWhatsAppSender is the only sender so far
│       └── health/        /api/ping, proves the stack end to end
└── app/src/
    ├── lib/api.ts         fetch wrapper, types, attaches the bearer token to every call
    ├── lib/auth.ts        OTP request/verify, token storage, decodes the JWT for display only
    ├── lib/upload.ts      XHR upload with progress, canvas downscale, EXIF drop
    ├── auth/              LoginPage (phone → code, parameterised by role)
    ├── owner/             OwnerPage, BedGrid, MediaManager, VisitRequests (accept/decline)
    └── seeker/            SearchPage, ListingPage (+ visit request), MyBookingsPage
```

App.tsx routes between owner/, seeker/ and login by a plain `window.location.pathname` switch, not
a router library — a handful of screens, ordinary `<a>` navigation is enough for now (see its comment).

## Data model (V1 + V2 + V3 migrations)

`app_user` → `pg_property` → `room` → `bed`, with `bed_state_log` as the audit trail.
`booking` (VISIT bookings only so far — RESERVE/tenancy is not built), `tenancy`, `rent_payment`
exist in V1; `tenancy`/`rent_payment` are not used yet.
`media` holds photos and one video per PG, with `is_cover` and partial unique indexes.
`otp_request` (V3) holds hashed, short-lived login codes — never the code itself.

Location is `geography(point,4326)`, written with `ST_MakePoint(lng, lat)` — longitude first.

## Auth

A phone number logs in via a 6-digit OTP (`auth/OtpService`) and gets back a 30-day HS256 JWT
(`auth/JwtService`). `JwtAuthFilter` decodes a valid bearer token on every request into an
`AuthedUser` request attribute, but never rejects a request itself — public reads (PG detail, bed
grid, media list) stay public. Every write calls `AuthedUser.require(...)` (401 if signed out) and
`.requireOwnerOf(pgOwnerId)` (403 if signed in as someone else) before touching data. A phone
number's role (`OWNER`/`SEEKER`) is fixed at its first-ever login and can never be changed by a
later login request. `OtpSender` is an interface; `LoggingOtpSender` (default) prints the code to
the server log, `Msg91OtpSender` is wired but has never been run against a real account — see
docs/features/2026-09-23-phone-otp-login.md.

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

`BedService` has three ways in, all sharing one private `performTransition`: `changeState` (the
owner's own bed grid, ownership-checked), `holdForVisit` (a seeker holding a bed themselves — not
an owner action, no ownership check, same conditional update) and `releaseHold` (decline, a
seeker's cancellation, or `booking/HoldExpiryJob`'s sweep — `actorId` is null for the sweep).

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

Owner signs in with a phone number and OTP → creates a PG → adds rooms (beds are created
automatically) → sees a colour-coded bed grid → changes bed states with audit logging → uploads
room photos and one walkthrough video → toggles the listing between DRAFT and LIVE. Every write is
checked against the signed-in owner; another owner's token gets 403, no token gets 401. A stranger
with no account can search LIVE PGs by area or nearby-me radius, budget, sharing, gender and food/AC,
and open a listing page with photos, video, room-by-room rent and vacancy. A seeker who signs in can
request a visit slot on a specific bed (which holds it), see their bookings with a countdown, and
cancel; the owner sees pending requests on that PG and can accept or decline; an unresolved hold
releases itself automatically within a minute of expiring.

## What does not exist yet

**Real SMS and WhatsApp delivery.** OTPs and booking notifications are logged to the server
console, not sent — `Msg91OtpSender` and a real WhatsApp Cloud API sender both need manual account
setup (MSG91 + DLT template registration; a Meta Business account + verified number + template
approval) that cannot happen from code, and neither has been run against a real account. Nothing is
deployable until this is done (see BACKLOG.md's Deploy item).

Also missing: a RESERVE/move-in booking flow (only VISIT bookings exist — accepting a visit does not
create a tenancy or move the bed toward BOOKED), rent register, notices, reviews, the Capacitor
mobile wrapper, and any deployment.

## Gotchas discovered so far

- Tests must not inherit `spring.datasource.url` from `application.yml`, or they silently run
  against the dev database.
- MP4 metadata is wiped in place (box renamed to `free`, payload zeroed), never spliced out:
  removing bytes from `moov` invalidates the absolute chunk offsets in a faststart file and the
  video stops playing.
- File deletes happen after the transaction commits, never inside it.
- Git Bash gives Node CLIs no TTY; run Claude Code from PowerShell.
- As of 2026-09-23, `./gradlew` (any task, including plain `compileJava`) fails in an agent-driven
  terminal session with `java.io.IOException: Unable to establish loopback connection` when it
  tries to fork a worker JVM — happens with and without `--no-daemon`, regardless of IPv4/IPv6
  preference. `D:\tmp_sock` has artifacts from successful runs the same day, so this is specific to
  that sandboxed session, not the machine — an actual terminal on this machine runs Gradle fine.
  If you hit this again from an agent session: don't fight it, ask the human to run the build in
  their own terminal.
