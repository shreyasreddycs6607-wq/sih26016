# Phase 3: Blocking Engine

## What exists

- **Decision engine** (`domain/blocking`, pure Kotlin, 19 tests): given "app X (window class Y) came to the front" it returns
  Allow or Block. Order: never-block safety list (calls, emergency, System UI, launchers, keyboards, FocusForce) ->
  strict-lock guards (Settings apps, uninstall dialog) -> your blocked list -> an active 5-minute pass.
- **Detection:** primary = `FocusAccessibilityService` (window-state events only, package + window class, no screen content).
  Fallback = `BlockerService` polling UsageStats once a second while the screen is on and the accessibility service is not
  connected (needs Usage access + Display over other apps). The Blocks tab shows which one is active.
- **Block screen** (`BlockActivity`): own task, not in Recents, Back goes Home. The active hero stands in a Guard pose,
  bursts in with the theme's spring, says an in-character line (4 per hero), plus a per-hero backdrop (HUD rings /
  moon / comic starburst) and banner ("ACCESS RESTRICTED" / "Rest now, sentinel" / "HALT!"). Actions: Take me home,
  optional 5-minute pass (3 a day), and none of that in strict mode.
- **Foreground service** (`BlockerService`, `specialUse`): persistent "N guarded apps" notification, START_STICKY, stops itself
  when blocking is switched off, holds a 1-pixel overlay so the block screen may launch from the background on Android 15.
- **Survival:** settings and the blocked list live in DataStore/Room, so process death and reboot lose nothing.
  `BootReceiver` (BOOT_COMPLETED, MY_PACKAGE_REPLACED) restarts the service; `FocusForceApp.onCreate` restarts it whenever the
  process starts; the Blocks tab restarts it whenever it is visible. The accessibility service is re-bound by Android itself.
- **App picker:** searchable, real icons, filter by Productive / Distracting / Neutral, "Select all N shown" (select by category).
  Calls, emergency, Settings, installers, launchers and FocusForce are never offered.
- **Strict lock:** choose 30 min / 1 h / 2 h / 4 h, confirm, done. For that time: no passes, blocking cannot be switched off,
  apps cannot be removed, the Settings guard cannot be turned off, and (if the guard is on) the Settings apps and the system
  uninstall dialog show the block screen. It can be extended, never shortened, and ends by itself. Enforced in
  `BlockingManager` through `BlockingRules`, which is unit-tested.
- Room v2 (`blocked_app` table) with a real 1 -> 2 migration.

## Verification status

- **CI built Phase 2 successfully** (tests + `assembleDebug`), so the Room/Hilt/WorkManager/navigation setup from Phase 2 is proven.
- Phase 3: all 37 domain tests run and pass locally. All new UI, view-model and manager code type-checks against Compose.
  NOT compiled locally: services, receiver, accessibility service, coordinator, DataStore repositories, Room entity/migration,
  `BlockActivity`, manifest. The GitHub Actions run on this push is the first compile of those.
- **Not exercised on a device by anyone yet.** Treat the checklist below as the real acceptance test.

## Test on a real device

1. Blocks tab: "How FocusForce notices apps" shows Accessibility once the service is enabled (Permissions screen), otherwise the polling fallback or "nothing can detect".
2. Add 2 apps (try Instagram/YouTube/Chrome). Use search, the category filter, and "Select all shown".
3. Switch Blocking on: a persistent "FocusForce is on guard" notification appears. Open a guarded app: the hero block screen covers it within
   a fraction of a second. Check all three heroes (Profile). Back and Take me home both land on the launcher, never in the app.
4. Use a pass: the app opens for 5 minutes, then the next switch back to it blocks again. After 3 passes the button disappears with a "No passes left today" note.
5. Turn passes off: the button never shows. Turn Blocking off: apps open normally and the notification disappears.
6. Kill FocusForce from Recents, open a guarded app: still blocked (accessibility). Reboot the phone: blocking resumes without opening the app (check the notification).
7. Turn Accessibility off and use the polling fallback (needs Usage access + Display over other apps): blocking should still trigger within about a second.
   Repeat on Android 12/13/14/15 if you can; Android 15 is the strictest about launching screens from the background.
8. Strict lock (use 30 min): confirm. Verify you cannot: switch Blocking off, remove an app, enable passes, turn the guard off; opening Settings or tapping an
   app's Uninstall shows "Settings are locked / Uninstalling is locked"; phone calls, the dialer and the notification shade still work. It ends at the shown time.
9. Battery-optimised OEM phone (Xiaomi/Oppo/Vivo/Samsung/realme): follow the tips on the Permissions screen, leave the phone idle for an hour, test blocking again.
10. TalkBack: the block screen announces the hero, the title and the two buttons; Back goes home.

## Known limitations (read these)

- **Strict lock is a speed bump, not a vault.** Safe mode, a computer with ADB, factory reset, force-stopping from some OEM task managers, or an
  OEM Settings page that is not on the guarded list can still switch FocusForce off. That is deliberate for emergencies.
- **Uninstall guard has gaps.** We only see package and window class names (no screen content). We block the Settings apps, vendor
  "security center" apps, and the system uninstall dialog. Uninstalling from the Play Store page, Samsung Device Care or a launcher long-press
  menu may not be intercepted on every phone.
- **The Settings guard blocks the whole Settings app** during a strict lock (Wi-Fi settings too), because window class names are not enough to tell pages apart.
- **Detection gaps:** split-screen/pop-up windows and apps that open inside another app's window (in-app browsers, Instant Apps) can slip through; Chrome Custom
  Tabs opened from an unblocked app are not attributed to the blocked app.
- **Android 12+ restricts starting foreground services from the background.** If the service is killed while the app is closed, it is restarted at the next app open,
  reboot or process start. The accessibility service keeps blocking without it, which is why it is the primary detector.
- **Android 14+ asks the user to confirm the "special use" foreground service type in Play Console,** and Android 15 limits background activity launches; the 1-px overlay
  anchor and the accessibility exemption cover this, but behavior varies by OEM.
- **Battery-optimised OEMs** (MIUI/HyperOS, ColorOS, Funtouch/OriginOS, One UI) can still kill background work. The permission screen lists the manual steps; they cannot be done for the user.
- **Polling fallback costs battery** (a 1-second UsageStats check while the screen is on) and is slower than accessibility.
- Pass state is per app per day (3 passes a day total). Midnight resets the count.
- Focus schedules/Pomodoro (Phase 4) and short-video/website blocking (Phase 5) are not here yet; "strict mode" today is a timed lock you start by hand.
- Play Console declarations needed before release: Accessibility API usage, `specialUse` foreground service, `QUERY_ALL_PACKAGES`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
