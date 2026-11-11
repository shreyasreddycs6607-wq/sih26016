# CLAUDE.md: FocusForce (orphan branch)

This branch is the **FocusForce Android app only**. It deliberately shares no history or files
with the Bhoomimitra monorepo on `main` (backend/frontend/ai-layer); do not merge it into
`main` and do not copy files between them. Work here is self-contained.

- Branch: `claude/focusforce-android-app-9ovpp5` (orphan, first commit has no parent).
- Read `README.md` for architecture, tokens and phase status; the highest-numbered `PHASEn.md` for the latest test checklist and known limitations.
- Rules for this app: original characters/art only (no resemblance to existing IP); all colors,
  sizes, shapes and durations come from `ui/theme/Tokens.kt`; every text pair must pass
  `ContrastTest` (WCAG AA); respect `LocalReduceMotion`; no screen content is ever logged,
  stored or transmitted; request only permissions that are actually used.
- Phases are delivered one at a time, each compiling before the next starts.
- The `main` branch rule from the monorepo still applies to GitHub: no force-pushing `main`.
