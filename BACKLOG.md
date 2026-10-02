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

### 1. Mobile app wrapper — partial, in progress
- Capacitor wrap of the same React app, Play Store listing, app icon, splash, push via FCM.
- Done when: the app installs from a link on your phone.
- **Done so far:** `app/android/` scaffolded and syncs cleanly (`npm run cap:sync`); push-notification
  client code wired up (logs the token, doesn't send anywhere yet). See
  docs/features/2026-09-24-mobile-app-wrapper.md.
- **Still needed, all manual/hands-on, not more code:** an actual Gradle build (this machine's
  Gradle has the same JDK bug as the API's — see docs/ARCHITECTURE.md Gotchas — so run
  `cd app/android && ./gradlew.bat assembleDebug` yourself, or use Android Studio), a signing
  keystore, a real app icon/splash, a Google Play Developer account (USD 25, a real payment — yours
  to do), and a Firebase project for real push sending.

### 2. Deploy — partial: files written, never run for real
- One VM in an Indian region, Postgres with nightly backup to R2, GitHub Actions deploy, staging + production.
- **Done so far:** Dockerfiles, Caddy, production compose, deploy/backup/restore scripts, systemd
  timers, CI + release + deploy workflows (all statically validated). See
  docs/features/2026-10-02-deploy-pipeline.md. **Still needed:** provision a VM and DNS, run
  `bootstrap-vm.sh`, create `/srv/roomfinder/.env`, set the GitHub Environment secrets, then watch the
  first release and fix whatever the first real run turns up.
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
