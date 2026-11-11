# Phase 2: Tracking + Insights

## What exists

- **Tracker:** `UsageStatsReader` reads UsageStatsManager events (package, activity class, timestamps only).
  `UsageAggregator` (pure Kotlin, unit-tested) turns them into per-app foreground time, launches and an
  hour-of-day histogram. Launchers, System UI and FocusForce itself are excluded from screen time.
  Unlock count = keyguard dismissals (Android 9+).
- **Persistence:** Room (`usage_daily`, `usage_hourly`, `daily_summary`, `app_category`), schema exported to
  `app/schemas`. A synced day with zero usage still gets a `daily_summary` row, so "no data" and "zero" differ.
  Rows older than 400 days are pruned.
- **Sync:** `UsageSyncer` (first run backfills 7 days, later runs fill every day since the last sync and
  re-sync the last one), a `UsageSyncWorker` every 30 minutes through WorkManager + Hilt, and an immediate
  sync whenever Home or Insights is visible (then every 30 s while visible).
- **Home:** today's total and goal ring, "x% more/less than yesterday / weekly average", unlock count, top 5
  apps, today's mission ("Stay under your goal"), and the villain of the week with a progress bar. Without
  Usage access it explains why and links to the permission flow.
- **Insights:** Daily (hourly bars, step back through 30 days) / Weekly (7 days) / Monthly (30 days) charts drawn
  in Canvas in each hero's style, goal line, tap-for-detail, productive vs distracting split, per-app list,
  per-app drill-down (14 days, opens, average), a 7-day x 24-hour peak-hours heat map, and an editable
  category screen (lazy list).
- **Tests added:** `UsageAggregatorTest` (8), `InsightsTest` (10): sessions, hour splitting, launches, orphan
  pauses, comparisons, mission thresholds, villain rules, category defaults and overrides.
- **CI:** `.github/workflows/android.yml` runs the unit tests and builds a debug APK on every push to this branch
  and uploads it as the `focusforce-debug-apk` artifact (Actions tab). Install it on your phone from there.

## Rules worth knowing

- **Mission status:** under 80% of goal = on track, 80-100% = at risk, 100%+ = over. It is computed live from
  today's total; nothing is stored. XP, ranks and streaks (and their tests) arrive with the gamification work.
- **Villain:** the most-used DISTRACTING app in the last 7 days (at least 5 min). Target = 80% of that app's use in
  the 7 days before; with no earlier data, 25% of the weekly goal; never below 30 min.
- **Weekly average:** mean of the synced days among the 7 days before today (days with no data are skipped, not zero).
- **Categories:** Android's own app category plus a list of well-known package names gives the default; your
  choice always wins and is stored locally.

## Verification status

Same sandbox limits as Phase 1 (no Android SDK, no Google Maven), so **the project has not been built here**.
- Type-checked against Compose: all new `domain`, `ui` (Home, Insights, charts, App detail, Categories, view
  models) and `AppInfoProvider` code. All 22 unit tests in `domain` run and pass (JUnit, plain JVM).
- NOT compiled or run: Room entities/DAOs/database, `UsageStatsReader`, `UsageSyncer`, `ExcludedPackages`,
  `UsageSyncWorker`, Hilt modules, `FocusForceApp`, nav routes (incl. `toRoute`), `MainActivity` change, manifest.
  The GitHub Actions build is the first real compile; read its log if it fails and send it to me.

## Test on a real device

1. Fresh install, grant Usage access in onboarding. Open Home: the ring and totals appear within a few seconds
   (shimmer first). Compare today's total against Digital Wellbeing / Settings > Digital Wellbeing: expect
   close but not identical (see limitations).
2. Use a couple of apps for a minute each, return to Home: they appear in Top apps with sensible times and opens.
3. Home mission / villain: set a tiny daily goal in Profile (30 min) and watch status change; check wording.
4. Insights: Weekly shows a "no data" dot for days before install, Daily shows hourly bars, the arrows step
   back, Monthly shows 30 slots. Tap bars for the readout. Flip the hero in Profile and revisit: charts and heat
   map change style (glow bars / soft bars / thick bars; squares / dust / halftone dots).
5. Tap an app: drill-down opens; change its category; go back: split and Home villain react. "Back to automatic" works.
6. Insights > Edit categories: scroll a long list smoothly; changes persist after killing the app.
7. Revoke Usage access in Settings, return: Home/Insights show the explanation card instead of stale numbers.
8. Leave the phone a few hours, reopen: yesterday and today are filled in (worker or catch-up sync).
9. TalkBack: ring, charts and bars announce a summary; heat map announces the peak hour.
10. Midnight: leave the app open past 00:00; Home switches to the new day within ~30 s.

## Known limitations

- **History starts at install.** Android keeps only about 7 days of usage events, so days before FocusForce's first
  sync cannot be recovered; Weekly/Monthly show "no data" dots for them. Monthly fills in over time.
- **Numbers will not match Digital Wellbeing exactly.** We sum activity foreground intervals from raw events. Picture-in-picture,
  split-screen, overlays and some OEM builds report events differently; a session open across midnight loses
  the pre-midnight part; work-profile / secondary-user apps are not included.
- **Unlocks:** need Android 9+ (shown only there). Phones without a lock screen emit no keyguard events, so we fall back to screen-on count.
- **Background sync is best effort.** OEM battery managers can defer WorkManager (see the OEM tips). Opening the app always
  catches up; if the app is not opened for more than ~7 days, the gap is lost.
- **Charts and TalkBack:** each chart announces a summary; individual bars are not separately focusable yet (tap readout is visual).
- **Default categories are heuristics** (a package list + Android's category) and will misjudge some apps; that is what the editor is for.
- `QUERY_ALL_PACKAGES` is now declared (needed for real app names/icons and launcher detection on Android 11+). Play requires a
  declaration; screen-time and blocker apps are an accepted use.
- Mission is a single live daily mission; weekly missions, XP, ranks and streak meter are not built yet.
