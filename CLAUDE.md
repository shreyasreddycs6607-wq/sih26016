# CLAUDE.md — SIH26016 / Bhoomimitra (repo root)

This is a **monorepo**, consolidated from three previously-separate repos on
2026-09-05. Read this file before touching git history or opening a PR here —
the consolidation is recent enough that assumptions from an older clone, or
from working in one of the original repos, will be wrong.

## What's in here

| dir | what it is |
|---|---|
| `sih26016-backend/` | FastAPI + Postgres/PostGIS. The live code — deployed, has real users hitting it. |
| `sih26016-frontend/` | React/Vite. Live, consuming the backend's `/openapi.json`. |
| `sih26016-ai-layer/` | **Historical reference only.** An early standalone prototype of the rules/KPI engine, superseded by `sih26016-backend/app/ai_layer/`. Its own `CLAUDE.md` says so at the top — don't build new work here, edits reach nothing running. |
| `docs/BUILD_BRIEF.md` | A rewrite brief for the backend (statutory stages, design laws, phased work order). Written as if for a greenfield repo — it isn't one, so read [[Phase 1 is additive]] context in git log / PR history before taking it literally. |

Each of `sih26016-backend/` and `sih26016-frontend/` has its own `CLAUDE.md`
with the actual build spec / design system for that half. Read the one for
whatever you're touching.

## History note

This repo's git history starts from a single "Consolidate backend, frontend
and ai-layer into one repo" commit — it does **not** carry the original
commit history from before the consolidation. If you're picking up work from
someone who was on one of the three original repos (`suhasisnice/sih26016-backend`,
`sih26016-frontend`, or a standalone `sih26016-ai-layer` prototype), their
local clone has no shared history with this one. Don't assume a fast-forward
works — diff content directly (`diff -r`, or clone both and compare) before
merging anything from a pre-consolidation clone, since content can and has
diverged in **both** directions across a boundary like that, not just one.

If you're verifying which side of a diff is "ahead" on a file both sides
touched, don't guess from surface style — check whether the file's own
imports/callers are consistent with one side and not the other (e.g. does a
shared, unmodified module already expose something only one side's version
actually calls). That caught a real case here: two versions of the same KPI
module looked like a coin-flip until checking `app/services/sla.py` — identical
on both sides — showed only one version actually used what it already exposed.

## Working here going forward

- **Collaborators:** `shreyasreddycs6607-wq` (owner/admin), `suhasisnice`
  (write), `shreyasreddycs93-a11y` (write).
- **Don't push to `main` directly, and don't force-push it.** Branch, push
  the branch, open a PR. `shreyasreddycs6607-wq` owns the repo; treat merges
  to `main` as needing their (or another collaborator's) review, not a
  unilateral action — this applies to you (the assistant) as much as to
  whoever's driving.
- If you're reconciling a divergent branch or an old clone into this repo,
  do it on a feature branch and open a PR describing what you found and why,
  the way you'd want to review someone else's reconciliation of your own
  work. Don't silently overwrite either side.
- Tag `@shreyasreddycs6607-wq` in the PR (or ask whoever's driving to) rather
  than assuming a merge will get noticed.
