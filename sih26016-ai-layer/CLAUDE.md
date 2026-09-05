# AI Layer — SIH26016

## SUPERSEDED — the live code moved to the backend repo

**Do not build new AI Layer work here.** The rules, KPIs and seed now live
in the backend repository, where the Build Guide always intended them:

    ../sih26016-backend/app/ai_layer/

That copy runs against Backend's real schema and Backend's `core/enums.py`,
and it is wired into `/dashboard/kpis`, `/dashboard/alerts` and
`POST /admin/run-rules`.

Everything in *this* repo predates that integration and uses the **old
vocabulary** — `verification` instead of `land_verification`, `rnr` instead
of `rehabilitation_resettlement`, objection status `open` instead of
`filed`/`under_review`, compensation `pending` instead of `awarded`,
severity `warning` instead of `high`. Running these rules against the real
database returns **zero alerts**, silently, because the strings no longer
match anything.

Keep this repo only as history. When something needs changing, change it in
the backend repo; edits made here reach nothing.

## What the live AI Layer contains now (backend repo)

As of the second build it is larger than what is preserved here, and the
extra modules have no counterpart in this repo at all:

    app/ai_layer/
      constants.py          thresholds, seed knobs, state/LGD reference
      loaders.py            rows -> plain dicts for the rules
      predict.py            NEW - forecasting and delay-risk scoring
      kpis/
        area.py  compensation.py  families.py  possession.py  rnr.py
        timeline.py           NEW - timeline adherence
        notices.py            NEW - notifications issued, awards declared
      rules/
        case_stalled.py  document_missing.py  objection_unanswered.py
        award_unpaid.py  possession_before_rnr.py
        timeline_breach.py    NEW - per-stage deadline, not a flat threshold
      seed/
        generators.py  anomalies.py  geo.py  reference.py
        pipeline.py           NEW - statutory notices and the proposal chain

`predict.py` is the answer to "where is the predictive analytics": an
empirical model over the office's own completed stage transitions, with the
evidence for every score returned alongside it. It deliberately uses no ML
dependency — on a system that makes decisions about people's land, a median
you can trace beats a model you cannot explain.

## Historical note on this repo

The AI Layer Build Guide assumes our code lives at `app/ai_layer/` inside the
Backend team's repo, sharing their Postgres schema. In practice this repo
(`sih26016-ai-layer`) is standalone — we have no access to a Backend repo or
schema yet. Until that changes:

- We own our own Postgres (Docker, `docker-compose.yml` in this repo root),
  with our own SQLAlchemy schema in `db/models.py` that approximates what
  Backend is expected to build (see the Handbook's field lists).
- Table/column names here should already follow the shared naming
  conventions below, so reconciling with the real Backend schema later is a
  rename, not a redesign.
- When a real Backend repo/schema shows up, the plan is: point `db/base.py`
  at their database (or drop this `app/ai_layer/` folder into their repo)
  and adjust `db/models.py` to match their actual tables.

## The project

A national land acquisition management system built for Smart India Hackathon 2026, problem statement 26016, published by the Department of Land Resources (Ministry of Rural Development).

Land acquisition in India follows stages set by the RFCTLARR Act 2013:
1. preliminary_notification — government announces intent to acquire
2. social_impact_assessment — study of who and what is affected
3. verification — survey and ownership records checked
4. objection_period — affected people may formally object
5. declaration — acquisition confirmed after objections handled
6. award — compensation amounts formally decided
7. rnr — rehabilitation and resettlement entitlements processed
8. possession — land physically handed over
9. monitoring — post-acquisition tracking

Use these exact stage keys. Do not rename or reorder them.

Important context: the department already runs a portal called LACRRIS which mostly records information after decisions are made. Our value is being the layer that actively runs the process — flagging what is stuck, missing or at risk. That framing is why the alert rules matter more than anything else we build.

## Who I am

I am on the AI Layer team (2 people). There are three teams:
- Frontend — React, builds every screen
- Backend — FastAPI + PostgreSQL, owns the database and the API
- AI Layer — us

## What we own

1. Rule-based alerts
2. The calculations behind five dashboard numbers
3. Realistic seed data for the whole team
4. Optionally, similar-case search using pgvector

## What we do NOT own — do not build these

- Any screen, component or frontend code
- API endpoints or routes (Backend owns these; we provide functions they call)
- Database schema or migrations, once a real Backend repo exists (Backend owns these — see the note above for the interim)
- Authentication or permissions

Our output reaches the screen through Backend's API. Frontend never imports our code.

## Stack

- Python, alongside a FastAPI + PostgreSQL backend
- PostGIS for spatial data, pgvector for optional similarity search
- Everything runs in Docker via docker-compose

## Naming conventions — shared by all three teams, non-negotiable

- Field names are snake_case: `is_overdue`, never `isOverdue`
- Dates are ISO strings: `"2026-08-25"`
- Status values are lowercase strings: `"pending"`, never `"Pending"`
- Use Python enums for anything with fixed values so typos cannot spread
- Money is stored in whole rupees as integers, never floats
- Area is stored in hectares as a float, rounded to 4 decimal places

## Deliverable 1 — Alert rules

Build these five. Every threshold lives in a single `constants.py` — never inline a number in rule logic.

RULE `case_stalled`
- Fires when: current stage unchanged for more than STALLED_DAYS (default 10)
- Severity: `warning` at 10+ days, `critical` at 20+ days
- Extra fields: `days_stalled`

RULE `document_missing`
- Fires when: a document required by the case's current stage is not present
- Requires a lookup table mapping each stage to its required document types — ask me for it if it does not exist yet
- Severity: `warning`
- Extra fields: `missing_document_types` (list)

RULE `objection_unanswered`
- Fires when: an objection has status `"open"` for more than OBJECTION_RESPONSE_DAYS (default 21)
- Severity: `critical` — this one has legal weight, an unanswered objection can invalidate an acquisition
- Extra fields: `objection_id`, `days_open`

RULE `award_unpaid`
- Fires when: an award exists but compensation status is still `"pending"` after AWARD_PAYMENT_DAYS (default 30)
- Severity: `warning`
- Extra fields: `beneficiary_count`, `amount_pending`

RULE `possession_before_rnr`
- Fires when: possession has been taken while R&R status is not `"completed"`
- Severity: always `critical`
- This is the most domain-aware rule we have. Resettlement is supposed to be settled before displacement. Keep it and make sure the seed data contains at least one case that triggers it, because it demonstrates real understanding of the law.

Every alert returns this exact shape:
```
{
  "case_id": int,
  "rule": str,            # the rule key above
  "severity": str,        # "warning" | "critical"
  "message": str,         # one plain sentence an officer can act on
  "detected_on": str,     # ISO date
  "details": dict         # the rule's extra fields
}
```

Rule design requirements:
- Each rule is a separate, independently testable function
- A rule takes data in and returns alerts out. It never writes to the database itself and never prints.
- Rules must be pure and deterministic: same input, same output, every time
- Never let one failing rule stop the others — collect results, isolate failures
- Write messages for a district officer, not a developer. "Stage unchanged for 14 days" beats "stalled_check triggered".

## Deliverable 2 — The five dashboard numbers

These five come directly from the official problem statement. Do not invent different ones or rename them.

1. `area_notified_ha` and `area_acquired_ha` — sum of parcel areas in hectares
2. `compensation_awarded_total`, `compensation_paid_total`, `compensation_pending_total` — whole rupees
3. `affected_families_count` — count of affected families, which is BROADER than landowners
4. `rnr_entitled_count`, `rnr_in_progress_count`, `rnr_completed_count`
5. `possession_taken_count`, `possession_pending_count` — counted in parcels

Each must be filterable by district and by project.

CRITICAL: compensation and R&R are separate things and must never be merged into one number. Compensation is money for land taken. R&R is housing and livelihood support for displaced people. A tenant farmer can be owed R&R while owning no land and receiving no compensation at all. Counting them together is factually wrong and a judge from this ministry would notice.

## Deliverable 3 — Seed data

Treat this as a first-class deliverable, not a chore. Every screen the other two teams build is only as convincing as the data inside it, and a dashboard showing three obviously fake rows is the most common way a hackathon demo falls flat.

Volume targets:
- 3 to 4 districts, 6 to 8 acquisition projects
- 40 to 60 cases, distributed across all nine stages — weight the earlier stages more heavily, which is what real caseloads look like
- 200 to 300 land parcels
- 300+ affected people, including a meaningful proportion who are affected families without land title
- 15 to 25 objections, some open, some resolved, at least two overdue
- Documents attached to most cases, deliberately absent on some

Realism requirements:
- Real Indian district and village names — pick one real state and stay inside it
- Survey numbers in the authentic format, e.g. `127/2A`, `45/1`
- Plausible owner names appropriate to the chosen region
- Parcel coordinates that genuinely fall inside the chosen district's real boundaries, so the map is not scattered across the ocean
- Areas, compensation amounts and family sizes in believable ranges — ask me if unsure rather than inventing wild numbers

Deliberate anomalies — roughly 15% of cases should be flawed on purpose so the alerts have something real to fire on:
- Several cases stalled past 10 and past 20 days
- Several missing a document their current stage requires
- At least two objections open past 21 days
- At least one award unpaid past 30 days
- At least one case with possession taken while R&R is incomplete

Engineering requirements:
- Use a FIXED random seed so regenerating produces identical data every time. The demo must never change shape unexpectedly.
- One command wipes and regenerates everything
- Generation must be fast enough to rerun casually — seconds, not minutes
- Print a short summary after generating: how many cases, parcels, people, and how many alerts should now fire

## Deliverable 4 — Similar-case search (OPTIONAL)

Do not start this until deliverables 1 to 3 are complete and working. If asked to start it early, remind me of this rule.

When built: embed objection text, store vectors in pgvector, return the most similar past objections and how they were resolved. This is the only genuinely AI part of our work — describe it accurately as semantic similarity search, never overstate it.

## Handling data safely

Authentication, permissions and role-based access are Backend's, not ours —
see "What we do NOT own". These rules are about the data itself and about
the damage our own code could do.

**Our functions are unprivileged.** A KPI or rule function computes over
whatever it is given. It must never be the place a decision about who may
see something gets made, and must never silently widen scope — if Backend
passes a district filter, never fall back to national totals when it is
missing or unrecognised. Return nothing, or raise. Backend decides who is
allowed to ask; we only answer the question asked.

**Alerts carry identifiers and counts, never personal details.** Alert
`message` and `details` reach the dashboard and may be visible to roles
that cannot see person-level records. Use `case_id`, `objection_id`,
`beneficiary_count`, `amount_pending` — never names, phone numbers or
addresses. The alert shapes above already follow this; keep it that way.

**Never generate national identifiers, even synthetic ones.** No Aadhaar,
PAN, voter ID, bank account or IFSC values in seed data, not even
obviously-fake ones. A synthetic Aadhaar number can still be a real
person's, and fabricated identity numbers in a government demo are
indefensible if anyone looks closely. If a screen appears to need one,
show a masked placeholder and ask me first.

**Seed data must stay obviously synthetic.** Real district and village
names are required for realism and are public record, so they are fine.
Personal data is not: phone numbers come from the fixed fake block in
constants, never sampled from the real mobile range. Do not add fields
that mint realistic personal identifiers just because a form has a slot
for them.

**Queries use the ORM or bound parameters — never string interpolation.**
KPI filters (`district`, `project`) arrive from Backend's API, which means
they originate from a URL. An f-string in a SQL statement is an injection
hole. Use SQLAlchemy expressions, or `text()` with bound parameters.

**Logs get identifiers, not records.** Log `case_id` and rule names. Never
log whole case or person rows — logs are the easiest place for personal
data to leak somewhere nobody is watching.

**Destructive operations are local-only and opt-in.** `run_seed()` deletes
every row in every table. It refuses to run against a non-local database
unless explicitly forced, because during integration week `DATABASE_URL`
may well point at Backend's shared database. Never remove that guard, and
never expose seeding or rebuilding through an API route.

**Similarity search (Deliverable 4) handles sensitive text.** Objections
are citizens' personal grievances. If embeddings are produced by a hosted
API, that text leaves this machine and goes to a third party — do not do
that without asking me first. Prefer a local embedding model.

## Rules versus machine learning

Default to plain written rules. We have no real historical data to learn from, rules behave identically every time, and we can explain exactly why any flag appeared when a judge asks. That explainability is worth more here than sophistication.

If you think machine learning genuinely fits something, say so — but state what data it would need, why a rule cannot do the job, and let me decide. Never describe a rule as if it were AI. Being accurate matters more than sounding impressive.

## Working in this repository

- Read the existing schema, models and code before writing anything new. Our own `db/models.py` is the source of truth for what data exists until a real Backend schema replaces it. Do not guess table or column names you could look up.
- If the repo contradicts something I have told you, say so rather than silently picking one.
- Never run a destructive command — dropping tables, deleting files, wiping the database — without asking me first and saying exactly what will be lost. This matters most for seed data.
- Never commit, push, merge or switch branches unless I explicitly ask.
- Prefer editing existing files over creating new ones. Do not create README or documentation files unless I ask.
- Keep rules, KPI calculations, seed generation and constants in clearly separate modules.

## How to work with me

- Build ONE small piece at a time. Never scaffold multiple features at once. Stop after each piece and wait for me to confirm.
- Before writing a function that reads from the database, tell me which tables and columns you intend to use so I can confirm they exist.
- Show me the shape of what a function returns before writing its implementation.
- Do not add any dependency without asking first.
- Be concise. Skip preamble.
- If something I ask conflicts with these rules, say so instead of quietly doing it.
- I am working with a teammate who is new to programming. When I ask you to explain something, explain it plainly and without condescension — assume intelligence, not experience.

## Definition of done, for anything we build

1. It runs against the real seed data without errors
2. Its output matches the exact shape agreed with the Backend team
3. Every threshold it uses lives in constants, not inline
4. Running it twice on the same data gives the same answer
5. Someone on another team could read the output and understand it without asking us
