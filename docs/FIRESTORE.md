# Firestore data model and security rules

The rules are in `/firestore.rules`. Everything is **denied by default**; each block below opens one path with the narrowest access that works.
No usage data (apps, time, sites, screen content) exists anywhere in this database: the app never sends it.

| Path | Who can read | Who can write | What is stored |
|---|---|---|---|
| `profiles/{uid}` | any signed-in user, **one doc at a time** (list is denied, so the user base cannot be scraped) | the owner, exact field set, validated | displayName (2-24), hero, rank, level (1-500), friendCode, hideFromLeaderboards |
| `friendCodes/{code}` | signed-in, get only | create by the owner of the `uid` inside; no update; delete by owner | `{ uid }` |
| `users/{uid}` | owner only | owner only, only `squadId` | which squad I am in |
| `users/{uid}/friends/{friendUid}` | owner only | owner creates (server timestamp) / deletes | `{ addedAt }` |
| `squadCodes/{code}` | signed-in, get only | created together with the squad by its owner (`getAfter` check); deleted by the owner | `{ squadId }` |
| `squads/{id}` | signed-in get (the random id is only reachable via the code; needed so joining can check size) | create by owner with `memberUids == [me]`; **join** adds only me and keeps size <= 12; **leave** removes only me (owner may hand over to a remaining member); last member may delete | name, code, ownerUid, memberUids |
| `squads/{id}/presence/{uid}` | members of that squad | the user for their own doc; state in IDLE/FOCUSING; `updatedAtMs` within 5 min of server time | focus state + when it ends |
| `squads/{id}/sessions/current` | members | a member starts one (`startedAt == request.time`, 5-120 min) or adds only themselves | the shared focus block |
| `leaderboards/{week}/entries/{uid}` | signed-in get; list only with `limit <= 100` | the owner; week id format; score integer 0-8000; `updatedAt == request.time`; **only if the profile says hideFromLeaderboards is false** | name, hero, rank, score, expireAt |

## Why each choice

- **`profiles` get-only:** a friend code or a squad is how you find a person; there is no "browse all players".
- **Codes are capabilities:** `squadCodes` and `friendCodes` allow a single lookup, never a list, so a code cannot be guessed by scanning. Codes are 6 characters of a 31-letter
  alphabet (about 887 million combinations) and joining requires being signed in; add App Check (below) to blunt scripted guessing.
- **`squads` update rules** are written as two exact patterns (JOIN, LEAVE) using `diff().affectedKeys()`, so a member cannot rename the squad, remove somebody else or take ownership.
- **Presence timestamp window** stops stale or forged "focusing" claims from being written far in the past/future.
- **Scores:** the rules bound the score (0-8000, the most a person can earn in a week) and force the timestamp to server time, **but a modified app can still submit any
  number in range**. Weekly XP is computed on the phone; true anti-cheat needs a server (Cloud Function) that sees the raw inputs, which this project does not have. The in-app text says so.
- **Hide from leaderboards** is enforced twice: the app removes your entry and the rules refuse to accept a new one while your profile says hidden.
- **Retention:** every leaderboard entry carries `expireAt` (now + 60 days). In the Firebase console enable a **TTL policy** on the collection group `entries`, field `expireAt`,
  so old weeks disappear by themselves. Account deletion in the app removes your entries for the last 9 weeks, your profile, friend code, friends and squad membership.

## Setting up (owner steps, cannot be done from code)

1. Create a Firebase project; add an Android app with package `com.focusforce.app` (and `com.focusforce.app.debug` for debug builds) and your SHA-1/SHA-256 fingerprints.
2. Enable **Authentication -> Google**. Download `google-services.json` into `app/` (it is git-ignored on purpose). Rebuilding with that file present applies the Google
   services plugin and turns multiplayer on; without it the app shows "Multiplayer is not connected" and sends nothing.
3. Create **Firestore** in production mode, then deploy `firestore.rules` (`firebase deploy --only firestore:rules`).
4. Enable the TTL policy above. Recommended: **App Check** (Play Integrity) to reject requests from modified or non-Play apps.
5. Test the rules in the Firebase **Emulator Suite** with the Rules Playground / `@firebase/rules-unit-testing` before launch. These rules have not been executed by anyone yet:
   the build environment used to write them had no Firebase emulator. At minimum verify: (a) a signed-out request is denied everywhere; (b) a user cannot read `users/{other}`;
   (c) joining a 12-member squad is denied; (d) a user cannot write another user's presence or entry; (e) `list` on `profiles`/`friendCodes`/`squadCodes` is denied;
   (f) a score of 8001 or a hidden profile is rejected; (g) a non-owner cannot delete `squadCodes/{code}`.
