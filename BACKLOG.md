# RoomFinder backlog

One feature per session. Ship it, test it, document it, commit it, then `/clear` and start the next.
Product context lives in the Claude project "product"; build rules live in CLAUDE.md.

## Done

- [x] Schema V1, local dev setup (docs/ops/local-setup.md)
- [x] Owner onboarding: create PG, rooms, auto-created beds
- [x] Bed grid with state machine, audit log, conditional-update concurrency
      (BedHoldConcurrencyTest — never delete)
- [x] Photos and video per PG (docs/features/2026-09-23-photos-and-video.md)
- [x] Phone OTP login (docs/features/2026-09-23-phone-otp-login.md)
- [x] Seeker search (docs/features/2026-09-23-seeker-search.md)
- [x] Visit booking with holds (docs/features/2026-09-23-visit-booking-with-holds.md)
- [x] Rent register and notices (docs/features/2026-09-23-rent-register-and-notices.md)

## Next, in order

### 1. Mobile app wrapper
- Capacitor wrap of the same React app, Play Store listing, app icon, splash, push via FCM.
- Done when: the app installs from a link on your phone.

### 2. Deploy
- One VM in an Indian region, Postgres with nightly backup to R2, GitHub Actions deploy, staging + production.
- Before this: set a real `AUTH_JWT_SECRET` and configure a real `OTP_PROVIDER` (MSG91 account, DLT
  template registration, live phone test), and a real WhatsApp Cloud API sender (Meta Business
  account, verified number, template approval) — all manual, non-code steps. See
  docs/features/2026-09-23-phone-otp-login.md, docs/features/2026-09-23-visit-booking-with-holds.md
  and docs/features/2026-09-23-rent-register-and-notices.md Known gaps.

## Later, not now

Reviews from placed tenants, instant-vs-request tiers with response timers, reserve token payment, seeker "my PG" screen, complaints log, i18n Tamil, nightly stays (needs legal review first).

## Rules

- No feature starts before the one above it is documented and merged.
- Every schema change is a new Flyway migration.
- Never cache bed availability; the database is the source of truth.
- Never hold rent or deposit money.
- Anything touching bed state or money gets read line by line before commit.
