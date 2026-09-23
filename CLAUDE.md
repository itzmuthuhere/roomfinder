# CLAUDE.md — RoomFinder

Read this before writing any code in this repo. These rules override general best practice.

## Read these first

1. `docs/product/PRODUCT.md` — what we are building and the product rules that shape features.
2. `docs/ARCHITECTURE.md` — current code layout, data model, conventions, what exists, what does not.
3. `BACKLOG.md` — the next feature and its acceptance criteria.
4. `docs/features/` — how each shipped feature actually works.

Keep `docs/ARCHITECTURE.md` current: update it in the same commit whenever a feature changes the
layout, the data model or the list of what exists.

## What this is

A monthly PG (paying guest) booking app for Chennai. Seekers find verified PGs with **real-time bed availability**, book a visit, and move in. PG owners run their daily operations (bed states, rent register, notices) in the same system, which is what keeps availability accurate.

Not in scope: nightly/short stays, rent collection by us, deposits held by us, lending, any brokerage.

Solo founder, employed full time, building nights and weekends. Optimise for shipping and for code I can still understand in three months.

## Current phase

Pre-launch. Owner app first, then seeker app. The launch gate is 25+ PGs and 500+ beds live in ONE Chennai area before any seeker traffic.

## Stack (decided — do not propose alternatives)

- Backend: Java 21, Spring Boot 3, one modular monolith, Gradle
- DB: PostgreSQL + PostGIS, Flyway migrations, no ORM magic beyond Spring Data JPA
- Frontend: React + Vite + TypeScript, Tailwind, one codebase for seeker and owner UIs
- Mobile: same React app wrapped (Capacitor or TWA). No React Native, no Flutter, no native code
- Media: Cloudflare R2, presigned uploads
- Messaging: WhatsApp Cloud API. Push: FCM. OTP: SMS provider (MSG91/2Factor)
- Maps: PostGIS for distance; MapLibre + OSM tiles for display. No Google Maps APIs
- Hosting: single VM in an Indian region, Postgres on the same host initially, nightly backup to R2
- Errors: Sentry. CI: GitHub Actions

## Hard rules

1. **No microservices, no Kafka, no Kubernetes, no Redis, no event sourcing, no CQRS.** One app, one database. If you think a queue is needed, use a Postgres-backed job table.
2. **Never hold user money.** No escrow, no rent collection, no deposits. Rent and advance go directly from tenant to owner. A small optional reserve token is the only exception and only via a licensed gateway.
3. **The database is the source of truth for bed availability.** Never cache availability. Never compute it from a counter.
4. **No new dependency** without a one-line justification in the PR description. Prefer the standard library and what is already here.
5. **No feature that is not in the build plan.** If it seems missing, ask; do not add it.
6. **Personal data stays in India.** Consent capture, export and delete endpoints exist from day one (DPDP obligations land May 2027).
7. Keep secrets in env vars. Never commit keys, phone numbers or real listing data.

## Domain invariants (break these and the product fails)

- A bed is a row with a state: `VACANT`, `HELD`, `BOOKED`, `OCCUPIED`, `ON_NOTICE`, `BLOCKED`.
- **Two seekers must never hold the same bed.** Holds are taken with a conditional update:
  `update bed set state='HELD' where id=:id and state='VACANT'` — zero rows updated means someone else won; surface that to the user immediately.
- Every state change writes a row to `bed_state_log` with actor and reason. No silent transitions.
- Holds expire automatically (24h monthly, 2h same-day) via a scheduled job. Expiry is never a manual step.
- Marking a tenancy as on notice sets the bed to `ON_NOTICE` with `available_from`, and it becomes bookable for that date. This is automatic, never a separate owner action.
- Only a real past or present tenant (`tenancy_id`) can leave a review.
- An owner's `instant_book` status is earned and revoked by the system based on accuracy, never set by hand.

## Code conventions

- Package by feature: `com.roomfinder.<feature>` (`bed`, `booking`, `pg`, `tenancy`, `rent`, `review`, `search`, `notify`), each with `web`, `service`, `repo` inside.
- Controllers are thin: validate, call the service, map to DTO. No business logic in controllers or repositories.
- Explicit DTOs for every API. Never return JPA entities.
- All money in paise as `int`/`long`. Never `double`, never `float`.
- All timestamps `timestamptz`, stored UTC, rendered in Asia/Kolkata.
- Errors: one `@ControllerAdvice`, consistent `{code, message}` body, real HTTP status codes.
- Migrations only through Flyway. Never edit an applied migration; add a new one.
- Frontend: hooks + plain fetch wrapper, no Redux. Mobile-first layout, works on a 5-inch screen on 3G.
- Tamil and English strings go through an i18n map from day one; no hardcoded user-facing text.

## Testing

- Postgres via Testcontainers for anything touching the database. No H2.
- **Required:** a concurrency test that fires 50 parallel hold requests at one bed and asserts exactly one succeeds. This test must never be deleted or weakened.
- Required: hold expiry job test, notice → `ON_NOTICE` → `VACANT` transition test, search filter test with PostGIS distance.
- Every bug fix ships with the test that would have caught it.

## When writing code for me

- Ask before: adding a dependency, changing the schema, changing a bed state transition, or touching payment code.
- Prefer small diffs. One feature per change.
- Say plainly when something in these rules is wrong for the task, rather than working around it silently.
- Do not generate README files, changelogs or docs unless asked.
- Do not refactor unrelated code while fixing something.

## Reference docs (product source of truth)

`roomfinder-build-plan.md` (schema, state machine, API list, weekend plan), `roomfinder-product-v1-chennai.md` (product definition, listing fields, booking tiers), `roomfinder-launch-kit.md` (field validation), all in the Claude project "product".
