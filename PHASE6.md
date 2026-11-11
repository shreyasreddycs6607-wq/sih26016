# Phase 6: Multiplayer

## What exists

- **Rank, XP, level and the streak "power meter"** (needed for profiles and leaderboards, and built first, with tests): XP is *derived* from your own history, never stored
  or editable: 1 XP per focused minute (completed Pomodoro), 50 XP per finished day under your daily goal, +5 XP per day already in the streak (up to +35). Only days that
  have ended count; a day with no data cannot extend a streak. Ranks: Recruit, Cadet (300 XP), Guardian (1,200), Sentinel (3,500), Champion (8,000), Legend (20,000). Level L
  starts at 25*(L-1)^2 XP. The Profile tab shows rank, level, XP bar, weekly XP and the meter; when a streak breaks the meter drains slowly and gently from the old value
  (no red, no shame text; the rank never goes down).
- **Google sign-in** (Credential Manager -> Firebase Auth), **profile** (name, hero, rank, level), **squads** created or joined by a 6-character code (max 12), **live presence**
  of who is in a focus block (heartbeat every minute, ignored when stale), **group Pomodoro** (anyone in the squad starts a 25/50-minute shared block; joiners get a local run
  that ends at the same moment, then one break), **friends** by friend code, **weekly leaderboards** (friends and everyone, ISO week keys like `2026-W11`), and
  **privacy controls**: edit your name, hide from leaderboards, stop sharing presence, leave a squad, sign out, and **delete all my multiplayer data + account**.
- **Firestore security rules** (`firestore.rules`) and a line-by-line explanation (`docs/FIRESTORE.md`).
- **Works with or without a Firebase project.** `SocialModule` picks the backend once: Firebase if the app was built with `app/google-services.json`; else, in debug builds only, an
  in-memory simulation (invented players, all named "(sim)", join code `DEBUG2`) so the screens can be seen; else "unavailable" (screens say so, nothing is sent).
  Release builds without Firebase never touch the network for multiplayer.

## What YOU must do for it to go live (I cannot do these from here)

1. Create a Firebase project and add the Android app(s) (`com.focusforce.app`, `com.focusforce.app.debug`) with your SHA fingerprints.
2. Enable Google sign-in; put `google-services.json` in `app/` (git-ignored) and rebuild. CI builds will not have it unless you add it as a secret and write it before the Gradle step.
3. Create Firestore and deploy `firestore.rules`; enable the TTL policy on `expireAt`; consider App Check. Details in `docs/FIRESTORE.md`.
4. Test the rules in the emulator (checklist in `docs/FIRESTORE.md`). **They have not been run.**
5. Update the Play Console Data safety form (name, hero/rank, squad membership, presence, score, account id) and the privacy policy; Google sign-in needs an OAuth consent screen.

## Tests (104 domain tests run and pass locally)

New: `XpTest` (derived XP, streak bonus and cap, gaps/missing days, today never penalised, focus minutes, ranks/levels, ISO week keys incl. 2025-W01 / 2020-W53 and weekly XP),
`SocialTest` (name sanitising, share-code alphabet/normalising, presence staleness, group-session join window and that a joined run ends with the shared block then one break,
leaderboard ordering/ties/hidden players/positions/score bounds). One test found a real bug (a one-block set was being clamped to two blocks), fixed in `PomodoroConfig.coerced()`.

## Verification status

- CI built Phases 2-5. Phase 6: domain code, view models and all screens type-check against Compose and the 104 tests pass locally.
- **Not compiled or run anywhere yet:** the Firebase backend and auth service (they compile only against Firebase/Credential Manager libraries, which are available in CI but not here),
  DataStore prefs, `SocialSync`, DI selection, Gradle plugin logic. The CI run on this push is their first compile.
- **No multiplayer code has ever talked to a real Firestore.** Expect integration fixes once a project is attached.
- The simulated backend lets you see and click through every screen in a debug build, but it proves nothing about the real backend.

## Test on a real device (after you attach Firebase)

1. Profile: rank/level/XP and the power meter appear; Squads and leaderboards opens the multiplayer screen. Without Firebase (release build) it says "not connected".
2. Sign in with Google; your friend code appears within a minute. On two phones: create a squad on one, join with the code on the other; both see each other.
3. Start a Pomodoro on one phone: the other sees "Focusing" within about a minute and "Idle" after it ends (and 3 minutes after killing the app/phone).
4. Group focus: start 25 min on one; the other taps Join: both end at the same moment, then one break.
5. Friends: add each other by code; Board -> Friends and Everyone show weekly XP; hide yourself: your entry vanishes on the other phone and the board says you are hidden.
6. Privacy tab: switch presence off (always Idle), rename, sign out, delete my data (both phones: the squad/friend/board entries disappear). If account deletion says to sign in again, do so.
7. Offline/airplane mode: screens show a network message, the rest of the app is unaffected.

## Known limitations

- **Scores are client-reported.** Rules bound them but cannot verify them; a modified app can post any value 0-8000. The board is "friendly competition". A Cloud Function would be needed for real anti-cheat.
- XP uses your *current* daily goal for every past day (the app does not store goal history), and only history the app has synced (about the last 7 days before install are recoverable, and history older than 400 days is pruned).
- Presence needs the foreground service to be running (blocking on), because that is what reports it; a phone without the service shows as Idle.
- Squad ownership transfers to the first remaining member when the owner leaves. There is no kick/ban, rename or squad chat.
- Sign-in uses Credential Manager and needs Google Play services; devices without it cannot sign in.
- Account deletion removes the last 9 weeks of leaderboard entries; older ones expire through the TTL policy you must enable.
- Remaining items from the gamification brief that are **not built**: weekly missions, unlockable badges, alternate hero color variants, unlockable block-screen lines, and per-hero cosmetic unlocks. Only the single daily mission, the weekly villain, XP, ranks and the streak meter exist.
- Firebase libraries add roughly 3-5 MB to the APK even when multiplayer is not configured.
