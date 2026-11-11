# Phase 1: Foundation + Onboarding

> **Update:** CI later built this phase's code successfully (Phases 1-5 are all green in GitHub Actions). It has still never been run on a device.

## What exists

- Gradle (Kotlin DSL + version catalog), Hilt, Navigation Compose with type-safe routes, DataStore, edge-to-edge single activity.
- Design tokens for VECTOR / NOCTIS / DASH in one file, applied through `FocusForceTheme(hero)`.
- Components: `FocusCard`, `FocusButton`, `StatRing` (gauge / moon phase / inked ring), `AnimatedCounter`,
  `shimmer()` / `ShimmerBox`, `FocusBottomBar`, `ThemedBackground`, 13 Canvas icons, haptics helper.
- Original Canvas art: 3 heroes x 3 poses (Idle / Guard / Celebrate) and the FocusForce logo.
- Onboarding: welcome boot-up -> live-themed hero pager -> goals -> daily goal -> guided permissions
  (explanation screen before each; Accessibility gets the prominent disclosure + "I agree").
- Main shell: Home, Focus, Insights, Blocks, Profile. Home shows live permission readiness and the saved
  goal; Profile has the hero/theme switcher and goal editor; Focus/Insights/Blocks are honest "arrives in
  Phase N" screens (nothing pretends to work).
- Permission status screen: live (refreshes on resume), deep links per permission, OEM tips for
  Xiaomi, Oppo, Vivo, Samsung, realme (the device's own vendor opens first).
- Debug-only component gallery: Profile -> "Component gallery" in debug builds.
- Tests: `OemTest`, `ContrastTest` (WCAG AA for every text pairing in every theme).

## Verification status (be aware)

This was written in a sandbox with no Android SDK and no access to Google Maven, so
**`./gradlew assembleDebug` has not been run.** What was verified instead:

- The `domain`, `permissions`, `ui/theme`, `ui/components`, `ui/art`, `ui/onboarding`, `ui/main`,
  `ui/permissions` and `ui/debug` sources type-checked against Compose 1.7 (JetBrains same-package
  artifacts), with small shims for Android-only APIs. That caught and fixed two real bugs.
- `OemTest` ran and passed. `ContrastTest`'s numbers were cross-checked in Python (all pairs >= 4.5:1),
  but the test itself was not run.
- NOT type-checked: `FocusNavHost`, `MainActivity`, Hilt wiring, `SettingsRepositoryImpl`, manifest and XML
  resources. Expect possible small fixes on first sync.

## Test on a real device

1. Fresh install: boot-up animation plays, the three heroes appear, Begin works.
2. Swipe the hero pager: the whole UI (colors, shapes, fonts, background) re-skins live. Pick one.
3. Goals: Continue stays disabled until one is chosen. Daily goal: slider snaps to 15 min, presets work, counter rolls.
4. Permissions, one at a time. For each: the explanation screen appears BEFORE Android's page; after you
   grant and press back, the step shows "Granted" without restarting. Check specifically:
   - Usage access opens the app's own entry (Android 10+) or the global list.
   - Overlay opens "Display over other apps" for FocusForce.
   - Accessibility shows the disclosure box; you must tap "I agree"; FocusForce appears in the list.
   - Notifications (Android 13+): system dialog; after denying, the button becomes "Open notification settings".
   - Battery: system dialog "Allow FocusForce to run in the background?".
   - Alarms (Android 12+): the special-access page. Below Android 12 / 13 those steps are skipped.
5. Kill the app mid-onboarding and reopen: it restarts onboarding (nothing is saved until the end). Finish
   it, kill the app, reopen: it goes straight to Home with your hero.
6. Home: the readiness ring matches reality. Revoke one permission in Settings, return: the ring updates.
7. Profile: tap each hero card, the theme changes instantly and persists after a restart. +/- 15 min edits the goal.
8. Permissions status screen: your phone's vendor tips are expanded first (test on Xiaomi / Oppo / Vivo /
   Samsung / realme if you have one).
9. Accessibility: TalkBack reads tabs as "tab, selected", rings announce "x of y", the disclosure is readable.
   Settings -> Accessibility -> Remove animations: ambient motion and springs stop. Font scale at maximum:
   nothing clips (screens scroll). Landscape and a tablet/emulator: content centres at 640dp.
10. Debug build: Profile -> Component gallery, flip through all three heroes.

## Known limitations

- Not compiled here (see above). Treat the first Gradle sync as the real build.
- The Accessibility service is inert; foreground detection arrives in Phase 3. The disclosure text already
  lists later features (short-video, browser address) that do not exist yet. Re-review it each phase so it
  never claims more or less than the service does.
- The daily goal is stored, but nothing consumes it until Phase 2/4.
- Shared-element transitions, the focus "power-up" animation, completion celebrations, the block screen and
  sound effects are not in this phase (the hero poses and spring system they need are).
- Dash has no light variant yet (alternate color variants are scheduled with the badge/unlock system).
- Hero names (Vector, Noctis, Dash) are everyday words. "Dash" is also the name of an existing animated
  character, so run a trademark and store-listing check before launch. Rename in `strings.xml` if needed.
- Tab switching uses in-screen state, not nested nav destinations (no per-tab back stack yet).
- Autostart / OEM battery settings cannot be granted programmatically on any OEM; the app can only guide the
  user. Reliability on MIUI/HyperOS, ColorOS, Funtouch/OriginOS and One UI depends on the user completing those steps.
- Android can auto-revoke permissions for unused apps (see the generic tip on the status screen).
