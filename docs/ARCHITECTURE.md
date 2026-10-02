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
│       ├── tenancy/       Move-in, notice, VacateJob sweep (ON_NOTICE -> VACANT, notice period over)
│       ├── rent/          rent_payment tracking, RentDueJob sweep, mark paid, WhatsApp reminder
│       ├── notify/        WhatsAppSender interface; LoggingWhatsAppSender is the only sender so far
│       └── health/        /api/ping, proves the stack end to end
├── app/
│   ├── capacitor.config.ts  appId com.roomfinder.app, webDir dist
│   ├── android/             Capacitor-generated native project (its own gitignore/gradlew) - not
│   │                        yet built; see ARCHITECTURE.md Gotchas and the mobile-wrapper feature note
│   └── src/
│       ├── lib/api.ts       fetch wrapper, types, attaches the bearer token to every call
│       ├── lib/auth.ts      OTP request/verify, token storage, decodes the JWT for display only
│       ├── lib/upload.ts    XHR upload with progress, canvas downscale, EXIF drop
│       ├── lib/push.ts      FCM registration (Capacitor push-notifications), no-op in the browser
│       ├── auth/            LoginPage (phone → code, parameterised by role)
│       ├── owner/           OwnerPage, BedGrid, MediaManager, VisitRequests, TenancyPanel
│       └── seeker/          SearchPage, ListingPage (+ visit request), MyBookingsPage
└── (docs/, api/ as above)
```

App.tsx routes between owner/, seeker/ and login by a plain `window.location.pathname` switch, not
a router library — a handful of screens, ordinary `<a>` navigation is enough for now (see its comment).

## Data model (V1 + V2 + V3 + V4 migrations)

`app_user` → `pg_property` → `room` → `bed`, with `bed_state_log` as the audit trail.
`booking` (VISIT bookings only so far — RESERVE, a separate move-in-reservation flow, is not built).
`tenancy` and `rent_payment` are live: a tenancy tracks one stay (`ACTIVE`/`ON_NOTICE`/`ENDED`),
`rent_payment` one row per tenancy per calendar month (`PENDING`/`PARTIAL`/`PAID`).
`bed.current_tenant_id` is set on move-in and cleared when the tenancy ends.
`media` holds photos and one video per PG, with `is_cover` and partial unique indexes.
`otp_request` (V3) holds hashed, short-lived login codes — never the code itself.
`pg_property.upi_vpa` (V4) is the owner's optional UPI id for rent reminders.

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

`BedService` has four ways in, all sharing one private `performTransition`: `changeState` (the
owner's own bed grid *and* `tenancy/TenancyService`'s move-in/notice calls — both are genuinely
owner actions, ownership-checked), `holdForVisit` (a seeker holding a bed themselves — not an owner
action, no ownership check, same conditional update), `releaseHold` (decline, a seeker's
cancellation, or `booking/HoldExpiryJob`'s sweep) and `endNoticePeriod` (`tenancy/VacateJob`'s
sweep — identical logic to `releaseHold`, a separate method only so each caller's name says what
actually happened). `actorId` is null for both scheduled sweeps.

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
releases itself automatically within a minute of expiring. An owner can move a tenant into a
VACANT/BOOKED bed (creating a tenancy, no separate step), see this month's rent per tenant and mark
it paid or send a reminder, and give notice — which sets the bed ON_NOTICE with its available-from
date in the same call and, once the vacate date passes, frees the bed and ends the tenancy on its own.

## What does not exist yet

**Real SMS and WhatsApp delivery.** OTPs, booking notifications and rent reminders are logged to
the server console, not sent — `Msg91OtpSender` and a real WhatsApp Cloud API sender both need
manual account setup (MSG91 + DLT template registration; a Meta Business account + verified number
+ template approval) that cannot happen from code, and neither has been run against a real account.
Nothing is deployable until this is done (see BACKLOG.md's Deploy item).

Also missing: a RESERVE/move-in-reservation booking flow (only VISIT bookings exist — accepting a
visit does not create a tenancy; an owner still moves a committed tenant in by hand), reviews, a
built/signed Android app (the Capacitor project in `app/android/` is scaffolded and syncs, but has
never been built or installed - see the mobile-wrapper feature note), and any deployment.

## Gotchas discovered so far

- Tests must not inherit `spring.datasource.url` from `application.yml`, or they silently run
  against the dev database.
- MP4 metadata is wiped in place (box renamed to `free`, payload zeroed), never spliced out:
  removing bytes from `moov` invalidates the absolute chunk offsets in a faststart file and the
  video stops playing.
- File deletes happen after the transaction commits, never inside it.
- Git Bash gives Node CLIs no TTY; run Claude Code from PowerShell.
- As of 2026-09-23, `./gradlew` (any task at all, even `--no-daemon`) fails in an agent-driven
  terminal session with `java.io.IOException: Unable to establish loopback connection`. Root cause,
  confirmed with a minimal repro: `java.nio.channels.Selector.open()` fails on this JDK/Windows
  combination because its internal wakeup pipe is built on a Unix-domain socket
  (`sun.nio.ch.UnixDomainSockets.connect0` → `Invalid argument: connect`), and Gradle's daemon
  protocol needs a working `Selector` no matter what. Plain `ServerSocket`/`Socket` loopback TCP is
  completely unaffected — only NIO `Selector`/`Pipe` is broken — so this is a real JDK/environment
  bug, not a sandbox wall, and it isn't fixable with JVM flags (tried forcing
  `java.nio.channels.spi.SelectorProvider` back to the classic `WindowsSelectorProvider`: same
  failure, same UnixDomainSockets code path underneath).
  **Workaround that works:** skip Gradle entirely and drive `javac`/`java` directly against the
  jars already in `~/.gradle/caches/modules-2/files-2.1` (download any newly-added dependency
  straight from Maven Central with `curl` if it's missing from the cache — Gradle never ran
  successfully in-session to resolve it). The one sharp edge: JDK `@argfile`s (needed because the
  full classpath is far past the Windows command-line length limit) treat backslash as an escape
  character, which silently mangles Windows paths — write every path in the argfile with forward
  slashes (`C:/Users/...`, not `C:\Users\...`); `java`/`javac` accept both interchangeably outside
  argfiles. This gets you real `javac` compilation and real execution of any test that doesn't need
  Testcontainers. Testcontainers-based tests still need Docker Desktop, which is a GUI app - it does
  not start from a headless/agent terminal session (confirmed: launching `Docker Desktop.exe`
  directly and polling for 2 minutes never produces a running process), so those still need a human
  to run them in their own terminal.
