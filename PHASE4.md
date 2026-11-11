# Phase 4: Limits, Schedules, Focus

## What exists

- **Daily limits** per app and per group. Warnings at 80% and 100% (each once per day, a jump straight past 80% sends only "reached"),
  then the app is blocked until midnight (the per-day notification state resets by date). Per-weekday rules (Mon-Sun mask).
  Usage for limits is read live from UsageStatsManager (not the 30-minute Room sync), only for apps that actually have a limit,
  cached for 5 s. Group limits sum all member apps and block all of them together.
- **App groups** (e.g. Social): create, rename, choose apps with the picker, delete (limits on a deleted group are removed).
- **Focus schedules / profiles**: named, weekday mask, start/end (overnight windows supported and belong to the day they start),
  blocked apps, blocked sites (stored only: see limitations), and a "silence notifications" toggle. Built-in starting points:
  Study, Sleep, Work, Family. A schedule that is running blocks its apps (passes allowed unless strict).
- **Pomodoro**: custom focus / short break / long break / blocks-before-long-break. While a FOCUS block runs, your guarded apps AND every app
  whose category is Distracting (last 30 days) are blocked, with no passes (stop the timer instead). A set ends after the long break.
  Phases are persisted with absolute times and advanced by an alarm (exact if allowed, otherwise Doze-friendly), by the foreground service,
  and at boot/app start, so a dead process or a sleeping phone does not lose the set. Ongoing countdown lives in the blocking notification
  (system chronometer, no per-second updates). History (completed and stopped-early blocks) with today / 7-day totals.
- **Signature moments:** a themed *power-up* when a focus session starts (HUD rings collapse / rising stars / comic "POWER UP!" starburst) and a
  themed *celebration* when a block completes while the app is open; haptic on both; static card with reduce-motion.
- **Do Not Disturb** (optional permission, new step in the permission flow): turned on to "priority only" while a silencing profile or Pomodoro focus
  runs, and restored to exactly what it was afterwards, never overriding a change you made yourself. Without the permission everything else still works
  and the Focus tab says so.
- **Mindful nudges / break reminders:** one gentle notification per unbroken stretch of phone use (default 45 min, 15-180), based only on when apps were
  in the foreground. Plus the limit warnings above and "profile started/ended" notifications.
- **Block screen** now explains the reason (guarded, limit reached, schedule, Pomodoro, strict guards) and offers passes only where they make sense.
- **Strict lock** also freezes existing limits, groups and schedules (no edit / disable / delete) and Pomodoro stop; new ones can still be added.
- Room v3 + real 2 -> 3 migration (groups, members, limits, schedules, Pomodoro history).

## Tests (68 domain tests run and pass locally)

New: `LimitsTest` (thresholds, groups, weekday rules, once-per-day warnings, midnight reset), `ScheduleTest` (inclusive start/exclusive end, weekends,
overnight windows, next boundary), `PomodoroTest` (cycle to long break and set end, catch-up after sleep, early stop, clamping), `NudgeTest`
(merging usage into stretches, real breaks, once per stretch), `EngineReasonsTest` (reasons, precedence, passes), `SiteListTest`.

## Verification status

- CI built Phases 2 and 3 successfully. All Phase 4 UI/view-model/domain code type-checks against Compose and 68 tests pass locally.
- NOT compiled locally: Room entities/DAO/migration, DataStore prefs, alarm/DND/notification code, receivers, coordinator and service changes,
  nav routes. The GitHub Actions run on this push is the first compile of those.
- Not tried on a device by anyone yet. Alarms, DND and background behaviour especially need real-phone testing.

## Test on a real device

1. Permissions: a new optional step "Do Not Disturb access". Grant it for the DND checks below, then also test with it off.
2. Limits: group "Social" (2 apps) with a 5-minute limit; use them. At 4 min you get a warning, at 5 the block screen says "daily limit reached"
   for both apps, and a "reached" notification appears once. A weekday-only limit must not trigger on a weekend day. A pass reopens the app for 5 min.
3. Schedule: create one that starts in 2-3 minutes (Study preset, edit times) with an app and "silence notifications". At the start time: a
   "profile started" notification, the app is blocked (even if you were already inside it, within ~5 s), DND turns on. At the end: DND returns to what it was.
   Test an overnight profile (Sleep) by setting it to span the current time.
4. Pomodoro: set focus 5 / short 1 / 2 blocks and start. You get the power-up moment; distracting apps are blocked with "no passes during a focus block";
   the notification counts down. At the end of focus you get a notification (and the celebration if the app is open); break unlocks apps; after the
   long break the set ends. Stop early (it records a partial block). Kill the app and reboot mid-set: it resumes.
5. Strict lock + Pomodoro/limits: you cannot stop the Pomodoro or edit/delete limits and schedules while locked.
6. Break reminders: set 15 min, use the phone continuously (pauses under 5 min do not reset): one reminder per stretch.
7. Turn the phone's "Remove animations" on: moments become a static card; TalkBack reads the moment text and the ring ("Focus, 12 minutes 3 seconds left").

## Known limitations

- **Website blocking is not enforced yet.** Sites on a schedule are stored and shown but only apps are blocked until Phase 5.
- Limit enforcement needs the foreground app to be detected (accessibility, or the 1-second usage poll). With accessibility off and the
  poll fallback, limits still work but react up to ~1-5 s late. A limit notification needs notifications enabled.
- Time in an app is read at the moment you open it and re-checked every ~5 s while you stay in it; the block can arrive a few seconds after the limit is crossed.
- Pomodoro "distracting apps" = category Distracting among apps used in the last 30 days at process start (edit categories in Insights). Apps you have never used are not included.
- Exact alarms need "Alarms and reminders" (Android 12+); without it alarms are inexact and Doze can delay phase changes by minutes. The service's 5-second check and the
  next app open catch up, but the phase notification itself may arrive late.
- Android 14+ may let users swipe away the ongoing notification when the foreground service is not running; the timer and blocking are unaffected.
- DND "priority only" respects your own DND exceptions (calls/alarms you allowed). We never use "total silence".
- Break reminders are based on screen-on foreground time and ignore launchers; screen-off listening (audio, calls) is not "use".
- Editing a limit/schedule/group during a strict lock is blocked entirely (even tightening it) to keep the rule simple.
- Schedules use the phone's local time zone; a time-zone change or DST shift re-arms at the next refresh (app open, boot or alarm).
