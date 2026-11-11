# Phase 7: Widgets, Polish, Monetization

## What exists

- **Glance home-screen widgets**, three of them, each drawn in the active hero's style (colors from the same tokens; gauges rendered to small bitmaps because
  widgets are RemoteViews): **Today** (screen time vs goal: HUD arc / moon phase / inked ring), **Quick focus** (start/stop a Pomodoro with one tap; opens the app
  instead if blocking cannot work yet), **Power meter** (the streak, 14 cells: glowing / moon dots / slanted). They refresh every 30 minutes, after every usage sync,
  when the hero changes, and on every Pomodoro start/stop/phase change.
- **Quick Settings tile** "Focus": tap to start/stop a focus set; shows Running / Tap to start.
- **Settings** (Profile -> Settings): hero/theme switch, language (Android's per-app language page on Android 13+; only English ships, and `locales_config.xml` says so),
  **CSV export** (usage per app per day, and focus sessions, via the system file picker; text cells are protected against spreadsheet formula injection),
  **Reset data** (two-step; wipes this phone's FocusForce data and restarts onboarding; refused during a strict lock; does not touch your online multiplayer account),
  in-app **privacy policy**, version.
- **Freemium with Google Play Billing behind an interface** (`BillingService`): `PlayBillingService` (one-time in-app product `focusforce_pro`, acknowledges purchases,
  ignores pending ones) for release; `FakeBillingService` (in-memory, free, forgets on restart) for debug builds only. `EntitlementRepository` is the single answer to "is this
  user Pro?": follows the store, caches the last verified answer, honours a cached "Pro" for 30 days when offline, and starts as free until the cache is read.
  **Free:** tracking, daily/weekly insights, 5 guarded apps, 1 schedule, 2 daily limits, Pomodoro, passes, widgets, tile, ranks, leaderboards, joining group focus.
  **Pro:** unlimited apps/schedules/limits, strict lock, short-video and website blocking, monthly insights, starting group focus, CSV export. These lines live in
  `ProPolicy` (one file, unit-tested) and are a product decision you should review. Losing Pro never deletes anything; it only blocks adding beyond the free limits.
- **Release config:** release builds are minified and resource-shrunk; signing comes from `keystore.properties` (git-ignored) or `FOCUSFORCE_STORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD`;
  with neither the release is left unsigned; `-PversionCode=N -PversionName=x.y.z` set the version. CI now also builds the unsigned **release** variant, which runs R8 and
  `lintVitalRelease` (that is the first time R8 has run on this code, so read that CI step if it fails).
- **Performance work done:** lazy lists with stable keys everywhere a list can be long (picker, categories); icon loading off the main thread with an LRU cache; screens read
  only what they draw (state hoisting, `derived` flows on `Dispatchers.Default`); chart/ring animations use a single `Animatable` and draw-phase reads; heavy background work
  is throttled (5 s foreground check, 250 ms content-inspection coalescing). A hand-written **baseline profile** (`app/src/main/baseline-prof.txt`) plus `profileinstaller`
  pre-compiles startup and main-screen code. It is coarse; replace it with a measured profile (see below).

## Tests (113 domain tests run and pass locally)

New: `CsvTest` (quoting, formula-injection guard for text but not for real numbers, CRLF document format, export ordering and rounding), `ProPolicyTest` (free limits,
bulk adds, downgrade with more than the free limit, every Pro feature gated, entitlement resolution online/offline/clock-skew).

## Verification status

- CI (GitHub Actions) is green for every phase, including Phase 7: unit tests, `assembleDebug` and the unsigned `assembleRelease` (R8 + lint vital) all pass. The only Phase 7 compile error was Glance's `actionStartActivity` (fixed). CI proves it compiles and the domain tests pass, not that it works on a phone.
- Locally verified: the domain code, view models and all screens type-check against Compose; 113 unit tests pass.
- Nothing in this project has been run on a phone by me or in CI (CI does not launch the app). **The first real test is yours.**

## What is NOT done (honest list)

- **Baseline profile is hand-written, not measured.** Generate one: add a `:baselineprofile` Macrobenchmark module, run `BaselineProfileGenerator` on a real device/emulator, copy the result over
  `baseline-prof.txt`. I could not run a device here.
- **No measured jank/performance numbers.** The list above is design, not measurement. Profile with Android Studio's Compose recomposition counts and `JankStats` before claiming "no jank".
- **Translations:** only English. The Language screen is wired to Android's per-app language; add `res/values-xx/strings.xml` (there are about 600 strings) and a `<locale>` line to add a language.
- **Empty and error states** exist where data can be missing (insights, picker, categories, social, rules, billing unavailable, offline multiplayer) but I did not audit every screen on a device.
- **Weekly missions, badges, hero color variants and unlockable block-screen lines** from the gamification brief remain unbuilt (see PHASE6.md). XP, ranks, the streak meter, the daily mission and the villain exist.
- **Sound effects** were never built (haptics are). **Shared-element transitions** were not built.
- **Play Console tasks** only you can do: create the `focusforce_pro` in-app product, upload a signed bundle, complete Data safety, the Accessibility declaration (with a demo video), the foreground-service
  (specialUse) declaration, `QUERY_ALL_PACKAGES`, battery-optimisation justification, a hosted privacy-policy URL (the in-app text is a draft), content rating, and testing tracks.
- The Quick Settings tile and widgets start a foreground service from the background; Android 12+ allows that for tile/widget taps, but some OEMs restrict it. Test on your target phones.

## Test on a real device

1. Long-press the home screen -> Widgets -> FocusForce: add all three. Today shows time vs goal (shimmer text "Syncing..." until the first sync); switch hero in Settings and watch all three restyle.
2. Quick focus widget: tap Start focus with blocking set up: it flips to "Until HH:MM"; tap Stop. With no detection permission it opens the app.
3. Edit the Quick Settings panel, add the Focus tile; tap it: it starts a set (tile Running) and again to stop.
4. Settings: change hero; open app language settings (Android 13+); export both CSVs (Pro: in a debug build the fake store unlocks it) and open them in a spreadsheet; a malicious-looking app name never executes.
5. Free vs Pro (debug build, fake store): add a 6th guarded app -> blocked with the Pro message; start a strict lock -> blocked; open Upgrade, tap Buy -> everything unlocks until the app restarts.
6. Release build: test with a Play internal-testing track and a licensed tester account to exercise the real billing flow, restore purchase after reinstall, and offline behaviour.
7. Reset data (not during a strict lock): the app restarts into onboarding; widgets show the new state; multiplayer sign-in is still there.
8. `./gradlew :app:assembleRelease` locally (or read the CI step) to confirm R8 and lint pass.
