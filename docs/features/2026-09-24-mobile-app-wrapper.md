# Mobile app wrapper (Capacitor)

- **Shipped:** 2026-09-24
- **Status:** partial — scaffolded and syncs correctly; not yet built, signed or installed on a
  device. See Known gaps: most of what's left is account setup and hands-on device testing, not code.
- **Related:** none new

## Why

CLAUDE.md's stack already decided this: "same React app wrapped (Capacitor or TWA), no React
Native, no Flutter, no native code." This is that wrapper — one extra native shell around the
exact same `app/` build, not a second codebase.

## How it works

```mermaid
flowchart LR
  A[npm run build] --> B[dist/]
  B --> C[npx cap sync android]
  C --> D[android/app/src/main/assets/public]
  D --> E[Android Gradle build\n(needs Android Studio\nor a working local Gradle)]
  E --> F[Signed APK/AAB]
```

`app/android/` is a standard Capacitor-generated Android Gradle project (its own `gradlew`, its own
`.gitignore` for build output and `local.properties`). `npx cap sync android` copies the latest
`dist/` web build into it and regenerates its plugin config — this step is pure Node/file-copy and
does not need Gradle, the Android SDK, or a connected device, so it's safe to run any time after
`npm run build`. Turning that synced project into an installable app is a normal Android Gradle
build (`assembleDebug` for a debug APK, `bundleRelease` for a signed Play Store AAB) - that step is
outside what this change could verify, see Known gaps.

`app/src/lib/push.ts` wires up `@capacitor/push-notifications`: `registerForPush()` is a no-op
outside a native build (`Capacitor.isNativePlatform()` is false in a browser, where there's nothing
to register with), otherwise it requests notification permission and registers for a token.
`addPushListeners()` logs the token and any incoming push - both are called once from `App.tsx` on
mount. Nothing sends a real push yet; see Known gaps.

## API

None new.

## Data

None new.

## Screens

None new — same screens, running inside a thin native shell instead of a browser tab.

## Background jobs

None.

## Config

| Key name | Where it lives | Required |
| --- | --- | --- |
| (none yet) | — | — |

A real FCM setup will need a Firebase project id and `google-services.json` dropped into
`app/android/app/` - not done yet, see Known gaps.

## Tests

None applicable - there is no server-side code in this change. The web build was verified with
`tsc --noEmit`, a full `vite build`, and a live browser smoke test (no console errors, no visual
regression) to confirm the Capacitor/push-notifications imports don't break the ordinary browser
build, which is the only part of this change that could be verified from this session.

## Known gaps

Genuinely manual from here - none of this can be done from code:

- **Never built.** `android/`'s own Gradle wrapper hits the identical JDK/environment bug documented
  in docs/ARCHITECTURE.md's Gotchas (confirmed with a fresh repro against this project specifically,
  not assumed) - so `assembleDebug` has never actually run. Run it yourself: `cd app/android &&
  ./gradlew.bat assembleDebug`, or open `app/android` in Android Studio and hit Run. An Android SDK
  is already installed on this machine (`C:\Android`, platform-tools + build-tools + platforms up to
  36.1) but no emulator (AVD) is configured and no device was connected to test with.
- **No signing key.** A release build for the Play Store needs a keystore generated and kept safe
  (`keytool -genkeypair ...`) - losing it means losing the ability to ever update the app again.
- **No Play Console listing.** Needs a Google Play Developer account (one-time USD 25 fee - a real
  payment, so this is for you to do, not something to automate), app icon and screenshots, a store
  description, a content rating questionnaire, and a privacy policy URL (RoomFinder collects phone
  numbers and location - DPDP-relevant, matches CLAUDE.md's "personal data stays in India" rule).
- **No real icon or splash screen.** `@capacitor/assets` can generate every density/resolution from
  one source image once you have a real logo; nothing was generated here since there's no brand
  asset to generate from yet.
- **No real FCM project or push-sending backend.** `registerForPush()` gets a token and only logs it
  - there's no endpoint to store a device token against a user, and no server-side code that calls
  Firebase Admin to actually send a push. Wiring that up needs a Firebase project (manual, like
  MSG91/WhatsApp) and is a small, separate follow-up once there's a real project to send to.
- **`Done when: the app installs from a link on your phone` is not yet met.** Once you have a debug
  APK from Android Studio, the fastest path to test on your own phone is `adb install
  app-debug.apk` over USB, or send yourself the file directly - a Play Store "link" implies the
  listing above, which is further out.
