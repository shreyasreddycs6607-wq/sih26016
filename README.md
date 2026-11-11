# FocusForce

Screen-time control, app blocker and focus app for Android with an original superhero
universe (Vector, Noctis, Dash). Kotlin, Jetpack Compose + Material 3, Hilt, Room,
DataStore, WorkManager, Glance. Offline-first; Firebase only for multiplayer (Phase 6).

> **This branch is an orphan.** It shares no files or history with the rest of the
> `sih26016` repository (the Bhoomimitra backend/frontend). Clone only this app with:
>
> ```bash
> git clone --single-branch --branch claude/focusforce-android-app-9ovpp5 --depth 1 \
>   https://github.com/shreyasreddycs6607-wq/sih26016.git FocusForce
> ```
>
> Open the `FocusForce` folder in Android Studio (Ladybug or newer, JDK 17).

## Status

| Phase | Scope | State |
|---|---|---|
| 1 | Foundation, design tokens, 3 themes, component library + debug gallery, onboarding, permission flow | built, see PHASE1.md |
| 2 | UsageStats tracking, Home, Insights, WorkManager sync into Room | **built, see PHASE2.md** |
| 3 | Blocking engine, block screen, app picker, strict mode | not started |
| 4 | Limits, schedules, Pomodoro, nudges | not started |
| 5 | Short-video and website blocking | not started |
| 6 | Multiplayer (Firebase) | not started |
| 7 | Widgets, tile, settings, billing, performance, release | not started |

## Architecture

Single `app` module, layered by package (split into Gradle modules later only if build times demand it):

```
com.focusforce.app
├─ domain/        pure Kotlin: models, repository interfaces, (later) use cases + rules engines
├─ data/          DataStore, (Phase 2+) Room, UsageStats, WorkManager workers
├─ permissions/   permission state checks, Settings deep links, OEM detection
├─ service/       AccessibilityService, (Phase 3+) foreground service
├─ di/            Hilt modules
└─ ui/
   ├─ theme/      design tokens (Tokens.kt), theme + CompositionLocals, hero copy
   ├─ components/ stat ring, card, button, bottom bar, counter, shimmer, backgrounds, icons
   ├─ art/        hero + logo illustrations drawn in Canvas (no image assets)
   ├─ onboarding/ ┐
   ├─ permissions/├─ feature screens, each with its own ViewModel where it has state
   ├─ main/       ┘
   ├─ debug/      component gallery (debug builds only)
   └─ nav/        type-safe Navigation Compose routes
```

### Planned data model (Room, from Phase 2)

| Table | Key columns |
|---|---|
| `usage_daily` | date, package, foregroundMs, launches |
| `unlock_daily` | date, count |
| `app_category` | package, category (PRODUCTIVE / DISTRACTING / NEUTRAL, user-editable) |
| `block_rule` | id, target (package or group), mode, dailyLimitMs, daysMask, strict |
| `schedule` | id, name, daysMask, startMin, endMin, blockedPackages, blockedSites, silenceNotifications |
| `pomodoro_session` | id, startedAt, endedAt, focusMin, breakMin, completed |
| `xp_event` | id, ts, source, amount |
| `mission` | id, kind, period, target, progress, state |
| `badge` | id, hero, unlockedAt |

Settings (hero, goals, daily goal, onboarding flag) live in DataStore today.

## Design tokens

All defined in `ui/theme/Tokens.kt`; components read `FocusTheme.*` and never hard-code values.
WCAG AA (4.5:1) for every text pairing is enforced by `ContrastTest`.

| Token | VECTOR (Engineer) | NOCTIS (Night Sentinel) | DASH (Courier) |
|---|---|---|---|
| background / surface / surfaceHigh | `#05090F` `#0B1620` `#12263A` | `#0D0F1F` `#161A33` `#222850` | `#101014` `#1C1C24` `#2A2A36` |
| primary / onPrimary | `#4DE8E0` / `#02151A` | `#D6DCF0` / `#0D0F1F` | `#FF5A5A` / `#101014` |
| secondary / tertiary | `#7AB8FF` / `#3DDC97` | `#9AA5E8` / `#F4E9B8` | `#FFD92E` / `#4D9BFF` |
| text / textMuted | `#E6F7F8` / `#8FB3B8` | `#F2F4FF` / `#A9B0CF` | `#FFFFFF` / `#C9C9D6` |
| shapes | chamfered (CutCorner) | soft rounded 24dp, pill buttons | slanted parallelogram panels |
| type | light sans + monospace numerals, wide tracking | serif display, sans body | black italic sans |
| stroke | 1dp hairlines | 1dp | 3dp ink outlines + hard offset shadow |
| motion (fast / medium / slow ms) | 140 / 280 / 520, crisp spring | 220 / 420 / 800, soft spring, 24s ambient | 100 / 220 / 400, bouncy spring (damping 0.45) |
| progress | 270° HUD gauge with ticks | moon phase (new → full) | thick inked ring |
| backdrop | grid + scanning line | stars + drifting fog | halftone dots + speed lines |

Spacing is a 4dp grid (4, 8, 12, 16, 24, 32, 48).

## Build

```bash
./gradlew :app:assembleDebug        # needs Android SDK 35, JDK 17
./gradlew :app:testDebugUnitTest
```

`local.properties` (with `sdk.dir`) is created by Android Studio and is git-ignored.

## Play policy notes

- **Accessibility:** a prominent in-app disclosure plus explicit "I agree" precedes the Settings
  page. The service is declared `isAccessibilityTool=false`, requests no window content, and
  currently ignores every event. The disclosure and the service config are widened only in the
  phase that ships the matching feature.
- **Exact alarms:** uses `SCHEDULE_EXACT_ALARM` (user-granted). `USE_EXACT_ALARM` is reserved by
  Play for alarm/calendar apps and is deliberately not used.
- **Battery optimization exemption:** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is restricted by Play
  to a short list of app categories. It may need a Play Console declaration or removal for the
  store build; the app falls back to the generic battery settings page and OEM tips.
- **Usage access / overlay:** special-access permissions granted by the user in Settings.
