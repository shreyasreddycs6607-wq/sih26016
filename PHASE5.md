# Phase 5: Short-Video + Website Blocking

## What exists

- **Short-video blocking** (Blocks tab -> Short videos and websites): per-app switches. When a short-video section (YouTube Shorts, Instagram
  Reels in the bundled rules) is on screen, the hero block screen appears with **Leave the short videos** (the accessibility service presses Back for
  you), Take me home, and an optional 5-minute pass for *that feed only*. The rest of the app keeps working.
- **Website blocking** for 9 browsers (Chrome, Chrome Beta, Firefox, Edge, Brave, Samsung Internet, Opera, DuckDuckGo, Vivaldi):
  - your own blocked sites (subdomains match, look-alikes such as `notreddit.com` do not),
  - built-in starter categories: Adult, Social media, Gambling (10 well-known domains each; deliberately short, not exhaustive),
  - keywords (3+ letters, matched case-insensitively against what the address bar shows),
  - sites listed on a running focus schedule (Phase 4 stored them; they are enforced now),
  - strict mode: sites, categories and keywords cannot be removed/switched off during a strict lock, and passes are unavailable. A pass unlocks one site only.
- **Remotely updatable detection rules** (`app/src/main/assets/rules_default.json`, schema below). A newer rules file can be **imported by hand** (file picker)
  or **downloaded daily** if the build is given an HTTPS URL (`RULES_URL` in `app/build.gradle.kts`, empty by default = no network use at all).
  Every candidate goes through a strict, all-or-nothing validator (size cap 512 KB, version >= 1, package/view-id/domain format checks, entry limits) before it is
  used or saved; downloads must be strictly newer; imports may be equal. A saved file that stops validating is ignored.
- **Privacy**, stated at the top of the screen and in the Accessibility disclosure: everything is on-device; the screen is read only for apps/browsers you switched on,
  for one decision, then dropped; nothing is logged, stored or sent. Implementation: `ScreenInspector` is the only code that touches the node tree and returns just
  (a) the set of view-id names (no text) and (b) the address-bar text, capped at 500 chars; the coordinator extracts the site name and discards the rest. The service
  subscribes to content-change events only while a rule is switched on, and inspects at most every 250 ms, only for apps a rule names.

### Rules file schema

```json
{
  "version": 2,
  "feeds":    [ { "packageName": "com.example.app", "label": "Example Reels", "verified": false,
                  "anyViewIds": ["com.example.app:id/reels_pager"], "allViewIds": [], "noneViewIds": [] } ],
  "browsers": [ { "packageName": "com.example.browser", "label": "Example", "verified": false,
                  "urlBarIds": ["com.example.browser:id/url_bar"] } ],
  "categories": [ { "id": "social", "domains": ["example.com"] } ]
}
```
A feed is "on screen" when ANY `anyViewIds` is present (if given) AND ALL `allViewIds` are present AND NONE of `noneViewIds` is. Unknown keys are ignored.
To find an app's ids on a device with a debugger: `adb shell uiautomator dump` and read `resource-id` attributes (development only; the app never does this itself).

## Tests (86 domain tests run and pass locally)

New: `RulesParserTest` (valid, unknown keys, garbage, one bad entry rejects the file, size cap, version rules, **the bundled default file itself is valid**),
`FeedMatcherTest`, `SiteMatchingTest` (URL/host parsing, subdomains vs look-alikes, keywords, short keywords, category + own + schedule sites, master switch),
`FeedAndSiteDecisionTest` (reasons, per-site/per-feed pass keys, strict, master switch).

## Verification status

- CI built Phases 2, 3 and 4 successfully. Phase 5: domain code, view model, screens and repository type-check against Compose/Android and all 86 tests pass locally.
- NOT compiled locally: DataStore prefs (`WebPrefs`), the updater worker, the accessibility service/inspector, coordinator changes, manifest/XML. CI on this push is their first compile.
- **The bundled rules have never been run against the real apps.** I could not look at the apps' view ids from here. The YouTube/Instagram ids and all browser address-bar ids are
  my best recollection and are marked `"verified": false` (the UI says "Experimental"). Expect to correct them on a real phone (see the test steps) and ship the fix as a rules update.

## Test on a real device

1. Turn on Accessibility, switch Blocking on. Blocks -> Short videos and websites: the privacy statement is at the top; the accessibility disclosure in the Permissions flow matches it.
2. Switch on YouTube. Open Shorts: block screen appears within about a second; **Leave the short videos** takes you back to the previous YouTube screen; normal videos and search are unaffected.
   Repeat for Instagram Reels. If it never triggers or triggers on normal screens, the view ids are wrong for your app version: dump them with `adb shell uiautomator dump` and
   import a corrected rules file (bump `version`).
3. Switch on Block websites + the Social category. In Chrome type `reddit.com`: blocked page screen shows the site name; "Leave this page" goes back. Check `old.reddit.com`,
   a keyword (add `poker`, type `poker odds`), a custom domain, and a lookalike (`notreddit.com` must NOT be blocked). Try Firefox, Edge, Brave, Samsung Internet if installed.
4. Pass: tap the 5-minute pass on a blocked site; that site opens, another blocked site still blocks. Strict lock: no pass, and you cannot remove a site/keyword or switch categories off.
5. Schedule sites: add `youtube.com` as a site on a schedule that is running now (Focus tab): Chrome blocks it while the schedule runs and stops after.
6. Rules: Import a valid file with a higher version (notice "Rules updated to version N"); try an invalid file, an older one and a huge one (each rejected with a message; nothing changes).
7. With no feature switched on, check (Settings -> Accessibility -> FocusForce, or a debugger) that only window-state events are delivered; nothing is read.
8. Update from Phase 4 on a phone that already has the service enabled: if the new features do nothing, switch the service off and on once (Android grants capabilities at enable time).

## Known limitations (please read)

- **Detection is only as good as the rules.** Apps rename view ids between versions and some obfuscate them (TikTok mostly does, and its whole app is a feed: block the app instead).
  Only YouTube Shorts and Instagram Reels ship, unverified. Facebook Reels, Snapchat Spotlight etc. need rules that someone has verified on a device.
- **Address-bar approach:** the bar often shows only the site name, or a search page URL without the words; keywords therefore catch what the bar shows, not page content. Private/incognito
  tabs, tabs opened inside other apps (in-app browsers, Custom Tabs launched from other apps), reader modes and browsers not in the list are not covered. It does not block
  by DNS/VPN, so another browser without a rule bypasses it (use the app block list for browsers you do not want).
- Some browsers hide the address bar while scrolling; the block triggers when it is visible or when the page changes.
- Category lists are small starter lists, not a full filter. They are not a substitute for parental-control DNS filtering.
- Pressing Back to leave a feed/page exits the app on some screens; that is acceptable here but is app-dependent.
- Play policy: the Accessibility declaration must now describe window-content access (view ids for chosen apps, address-bar text for browsers), with the in-app disclosure
  shown before the permission, which is implemented. Expect Play review to ask for a demo video. `INTERNET` is declared for the optional rules download only.
- Strict lock does not stop you disabling the service from an OEM settings page that is not guarded (see Phase 3 limitations).
- Remote updates need a hosted file and `RULES_URL` set at build time; there is no server in this repository. Anyone who controls that URL controls the rules, so host it
  somewhere you control, over HTTPS (HTTP is refused). Rules can only name view ids and domains; they cannot execute anything.
