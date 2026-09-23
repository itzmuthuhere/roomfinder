# RoomFinder backlog

One feature per session. Ship it, test it, document it, commit it, then `/clear` and start the next.
Product context lives in the Claude project "product"; build rules live in CLAUDE.md.

## Done

- [x] Schema V1, local dev setup (docs/ops/local-setup.md)
- [x] Owner onboarding: create PG, rooms, auto-created beds
- [x] Bed grid with state machine, audit log, conditional-update concurrency
      (BedHoldConcurrencyTest — never delete)
- [x] Photos and video per PG (docs/features/2026-09-23-photos-and-video.md)

## Next, in order

### 1. Phone OTP login
Removes the `X-Owner-Id` header hack and `/api/dev/owners`.
- Backend: request OTP, verify OTP, issue JWT; owner vs seeker roles; every PG/bed write checks ownership.
- Provider: MSG91 or 2Factor behind an interface; a dev implementation that logs the code instead of sending it.
- Done when: an owner can only see and edit their own PGs, and DevController is deleted.

### 2. Seeker search
- Backend: `GET /api/search` with area or lat/lng radius (PostGIS), budget, sharing, gender, AC, food; only LIVE PGs with at least one bookable bed.
- Frontend: search screen, result cards with cover photo, rent, distance, vacancy; listing page with all details, rules, documents required.
- Done when: a stranger can find a real PG near a landmark on a phone.

### 3. Visit booking with holds
- Backend: `POST /api/bookings/visit` takes a bed hold (VACANT → HELD) with expiry; scheduled job returns expired holds to VACANT; owner accept/decline; WhatsApp confirmation to both sides.
- Frontend: slot picker, my bookings with countdown, cancel.
- Done when: a visit can be booked and the bed frees itself if nothing happens.

### 4. Rent register and notices
The reason owners keep the app open daily, and what keeps availability honest.
- Backend: tenancy, rent_payment, mark paid, pending list, WhatsApp reminder with UPI link; notice sets ON_NOTICE plus available_from automatically.
- Frontend: register screen, one-tap reminder, notice flow.
- Done when: an owner's monthly collection is easier here than in a notebook.

### 5. Mobile app wrapper
- Capacitor wrap of the same React app, Play Store listing, app icon, splash, push via FCM.
- Done when: the app installs from a link on your phone.

### 6. Deploy
- One VM in an Indian region, Postgres with nightly backup to R2, GitHub Actions deploy, staging + production.
- Do not deploy before item 1 (auth) exists.

## Later, not now

Reviews from placed tenants, instant-vs-request tiers with response timers, reserve token payment, seeker "my PG" screen, complaints log, i18n Tamil, nightly stays (needs legal review first).

## Rules

- No feature starts before the one above it is documented and merged.
- Every schema change is a new Flyway migration.
- Never cache bed availability; the database is the source of truth.
- Never hold rent or deposit money.
- Anything touching bed state or money gets read line by line before commit.
