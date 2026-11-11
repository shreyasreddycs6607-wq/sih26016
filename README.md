# FocusForce

Screen-time control, app blocker and focus app for Android with an original superhero universe (Vector, Noctis, Dash).
Kotlin, Jetpack Compose + Material 3, Hilt, Room, DataStore, WorkManager, Glance. Offline-first: Firebase is used only for the optional multiplayer
(Phase 6) and nothing about your usage ever leaves the phone.

> **This branch is an orphan.** It shares no files or history with the rest of the `sih26016` repository (the Bhoomimitra backend/frontend). Clone only this app with:
>
> ```bash
> git clone --single-branch --branch claude/focusforce-android-app-9ovpp5 --depth 1 \
>   https://github.com/shreyasreddycs6607-wq/sih26016.git FocusForce
> ```
>
> Open the `FocusForce` folder in Android Studio (Ladybug or newer, JDK 17).

## Status

All seven phases are written. The build is checked by GitHub Actions on every push (unit tests, debug APK, unsigned release build with R8); the debug APK is the
`focusforce-debug-apk` artifact of each run. **Nobody has run the app on a phone yet**, so the per-phase "Test on a real device" lists are the real acceptance tests.
Each phase document lists exactly what was and was not verified and the known limitations.

| Phase | Scope | Document |
|---|---|---|
| 1 | Foundation, design tokens, 3 themes, component library + debug gallery, onboarding, permission flow | [PHASE1.md](PHASE1.md) |
| 2 | UsageStats tracking, Home, Insights, WorkManager sync into Room | [PHASE2.md](PHASE2.md) |
| 3 | Blocking engine, block screen, app picker, strict lock | [PHASE3.md](PHASE3.md) |
| 4 | Limits, schedules, Pomodoro, nudges | [PHASE4.md](PHASE4.md) |
| 5 | Short-video and website blocking, updatable rules | [PHASE5.md](PHASE5.md) |
| 6 | Multiplayer (Firebase), ranks and XP | [PHASE6.md](PHASE6.md), [docs/FIRESTORE.md](docs/FIRESTORE.md) |
| 7 | Widgets, tile, settings, billing, release config | [PHASE7.md](PHASE7.md) |

Not built from the original brief: weekly missions, unlockable badges, hero color variants, unlockable block-screen lines, sound effects, shared-element transitions,
translations, a measured baseline profile (details in PHASE6.md and PHASE7.md).

## Architecture

Single `app` module, layered by package (split into Gradle modules only if build times demand it). Pure Kotlin logic lives in `domain/` and is unit-tested without a device
(113 tests); everything Android-specific is behind small interfaces or in `data/`.

```
com.focusforce.app
├─ domain/      pure Kotlin: usage aggregation, insights, blocking decisions, limits, schedules, Pomodoro, nudges, rules parsing and site matching,
│               XP/ranks/streaks, social models, CSV export, free/Pro policy; repository and backend interfaces
├─ data/        Room (db/), DataStore prefs, UsageStats reader + sync, blocking coordinator, focus + Pomodoro controllers, web rules, social (Firebase /
│               simulated / unavailable), billing (Play / fake), data reset + export
├─ permissions/ permission state, Settings deep links, OEM detection
├─ service/     accessibility service + inspector, foreground service, boot/alarm receivers, Quick Settings tile
├─ widget/      Glance widgets and the data they show
├─ di/          Hilt modules (backend, billing and database selection)
└─ ui/          theme/ (design tokens), components/, art/ (Canvas heroes), and one package per feature area; nav/ has the type-safe routes
```

Storage: Room database `focusforce.db` (usage_daily, usage_hourly, daily_summary, app_category, blocked_app, app_group, group_member, limit_rule, focus_schedule,
pomodoro_session; migrations 1->2->3, schema exported to `app/schemas`) and DataStore files for settings, blocking, focus, web, social and Pro status.

Selection of backends happens once, in DI: multiplayer uses Firebase if `app/google-services.json` exists, otherwise a debug-only in-memory simulation, otherwise an inert
"unavailable" backend; billing uses a fake store in debug builds and Google Play in release builds.

## Design tokens

All defined in `ui/theme/Tokens.kt`; components read `FocusTheme.*` and never hard-code values. WCAG AA (4.5:1) for every text pairing is enforced by `ContrastTest`.

| Token | VECTOR (Engineer) | NOCTIS (Night Sentinel) | DASH (Courier) |
|---|---|---|---|
| background / surface / surfaceHigh | `#05090F` `#0B1620` `#12263A` | `#0D0F1F` `#161A33` `#222850` | `#101014` `#1C1C24` `#2A2A36` |
| primary / onPrimary | `#4DE8E0` / `#02151A` | `#D6DCF0` / `#0D0F1F` | `#FF5A5A` / `#101014` |
| secondary / tertiary | `#7AB8FF` / `#3DDC97` | `#9AA5E8` / `#F4E9B8` | `#FFD92E` / `#4D9BFF` |
| text / textMuted | `#E6F7F8` / `#8FB3B8` | `#F2F4FF` / `#A9B0CF` | `#FFFFFF` / `#C9C9D6` |
| shapes | chamfered | soft rounded, pill buttons | slanted parallelogram panels |
| type | light sans + monospace numerals | serif display | black italic sans |
| progress | 270 degree HUD gauge | moon phase | thick inked ring |
| motion (fast / medium / slow ms) | 140 / 280 / 520 | 220 / 420 / 800 | 100 / 220 / 400, bouncy spring |

Spacing is a 4dp grid (4, 8, 12, 16, 24, 32, 48).

## Build

```bash
./gradlew :app:assembleDebug            # needs Android SDK 35, JDK 17
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease          # unsigned unless keystore.properties / FOCUSFORCE_* env vars are present
```

Release signing: create `keystore.properties` in the repo root (git-ignored) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`, or set `FOCUSFORCE_STORE_FILE`,
`FOCUSFORCE_STORE_PASSWORD`, `FOCUSFORCE_KEY_ALIAS`, `FOCUSFORCE_KEY_PASSWORD`. Set the version with `-PversionCode=N -PversionName=x.y.z`.
Multiplayer: add `app/google-services.json` (see PHASE6.md). Remote rules updates: set `RULES_URL` in `app/build.gradle.kts` (see PHASE5.md).

## Play policy notes

- **Accessibility:** a prominent in-app disclosure and explicit "I agree" precede the Settings page. The service starts with window-state events only; screen-content events are added at runtime only while a
  short-video or website rule is switched on, and the app reads view ids / one address-bar field for one decision and drops it.
- **Foreground service:** `specialUse` (app blocker). **Exact alarms:** `SCHEDULE_EXACT_ALARM` (user-granted), not `USE_EXACT_ALARM`. **`QUERY_ALL_PACKAGES`** for real app names/icons and launcher detection.
- **Battery optimisation exemption** is restricted by Play to a short list of app categories and may need a declaration or removal for the store build.
- **INTERNET** is used for multiplayer (when configured), the optional rules download and Play Billing only.
