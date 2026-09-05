# BUILD BRIEF — National Land Acquisition & Management System (SIH26, PS 26016)

You are the backend engineer on this project. This file is the authoritative spec.
Read it fully before writing any code. Do not skim it.

---

## 0. Context you need

**What this is:** a web platform that digitises the full land acquisition lifecycle in
India — from a project proposal, through statutory notifications, objections, awards,
compensation and possession, to rehabilitation & resettlement of displaced families.
Users are government officers (district → state → centre) plus a read-only public view.

**Stack (locked, do not change):**
- Backend: FastAPI + Pydantic v2
- DB: PostgreSQL with PostGIS and pgvector, running in Docker
- Migrations: Alembic
- Frontend: React (built by a separate pair — you do NOT touch frontend code)
- Files: stored in a Docker volume beside the database
- Runs locally for the demo

**Constraint:** this is a hackathon build with a hard deadline. Depth on the critical
path beats breadth. A half-built module is worth less than a modelled-but-unbuilt one.

**Before anything else:** if you are mid-way through a bug fix on the deployed version,
finish or cleanly park it and commit. Then create a new branch `feat/acquisition-core`.
Do not interleave the bug fix with this work.

---

## 1. FIRST TASK — audit, then plan, then STOP

Do not start implementing. Do this first:

1. Read the existing repo. Report: current models, migrations, routers, auth setup,
   test setup, docker-compose contents, and what already works.
2. Map what already exists onto the schema in section 4 and the phases in section 7.
   Say explicitly what can be reused, what must be changed, and what is missing.
3. Produce a phase-by-phase plan with file-level detail for Phase 1 only.
4. **Stop and wait for approval.** Do not write code until the plan is approved.

---

## 2. The single most important concept

The core object is **a land parcel within a project** — one survey number, in one
village, with its own owners, its own notification dates, its own award, its own
payment status, its own possession date.

Every dashboard number in this system is an aggregation over parcels. Nothing else.

---

## 3. NON-NEGOTIABLE DESIGN LAWS

These are hard constraints. If a design decision conflicts with one of these, the law wins.
If you believe a law is wrong, say so and stop — do not silently work around it.

**Law 1 — Records are born in the workflow, never typed in afterwards.**
No API endpoint may write directly to an aggregate or a reported total. Every figure
the dashboard shows is computed with SQL over records created by workflow actions.
If you find yourself adding a `total_compensation` input field, you have made a mistake.

**Law 2 — Corrections are a feature, not a support ticket.**
Every table is editable by an authorised role, but an edit requires a reason, retains
the previous value, and stamps actor + timestamp. Implement this ONCE as a generic
`audit_log` table plus a PostgreSQL trigger applied to all business tables. Audit
history means nothing is lost — it does NOT mean nothing can change.

**Law 3 — One parcel, one identity, nationally.**
`ulpin` (the 14-digit ULPIN / Bhu-Aadhaar) is the external parcel key. Unique project
codes. On project creation, run a fuzzy match on (name, district, requiring_body) and
return a duplicate warning before insert.

**Law 4 — The law is data, not code.**
Statutory stage sequences live in `statute` + `stage_definition` tables. RFCTLARR 2013
and the National Highways Act 1956 are two sets of ROWS, not two code paths. There must
be exactly one state-transition function, driven by config. Adding a state act must be
an INSERT, never a code change.

**Law 5 — A parcel has many people, and not all of them own it.**
Never a single `owner_name` column. Many-to-many between parcel and person with a
`relationship_type`: owner, co_owner, tenant, sharecropper, landless_labourer,
community_holding. R&R entitlements go to people who own nothing.

**Law 6 — Official geometry and field geometry are two different things.**
Every parcel stores `geom_official` (from cadastral records) AND `geom_field`
(captured by a field officer), plus a computed area difference and a discrepancy flag.
Never overwrite one with the other.

**Law 7 — States speak different dialects; keep a translator.**
All external land-record access goes through one `LandRecordAdapter` interface with
per-state implementations. Ship a stub adapter. Keep a vocabulary mapping table
(survey_number / khasra / khata / patta / RTC → one canonical field).

**Law 8 — Every statutory deadline is a live clock.**
Deadlines come from `stage_definition.deadline_days` measured from another stage's
event date. They are computed, never stored as a static "due date" that can drift.

---

## 4. SCHEMA (target shape — adapt names to existing conventions, keep the structure)

**Config / reference**
- `statute` — id, code (RFCTLARR_2013 | NH_ACT_1956), name
- `stage_definition` — id, statute_id, seq, code, name, section_ref, deadline_days,
  deadline_from_stage_code, is_terminal, on_breach (lapse | warn)
- `vocabulary_map` — state, external_term, canonical_field

**Core**
- `requiring_body` — id, name, type (central_ministry | state_dept | psu | private | ppp), sector
- `project` — id, code UNIQUE, name, sector, requiring_body_id, statute_id, state,
  district, consent_threshold_pct (nullable), status, created_by, created_at
- `parcel` — id, project_id, ulpin, survey_number, village, taluk, district, state,
  area_official_ha, area_field_ha, area_diff_pct (generated), has_geometry_discrepancy,
  geom_official geometry(MultiPolygon,4326), geom_field geometry(MultiPolygon,4326),
  current_stage_id, GIST index on both geometry columns

**Lifecycle**
- `statutory_event` — id, parcel_id (nullable), project_id, stage_definition_id,
  event_date, gazette_ref, document_id, actor_id, created_at
  → THIS is what starts clocks. A notification is an event, not a file.

**People**
- `interested_person` — id, name, father_or_spouse_name, contact, bank_account_ref
- `parcel_interest` — parcel_id, person_id, relationship_type, share_fraction
- `affected_family` — id, project_id, head_person_id, member_count, is_displaced,
  consent_status, consent_date, consent_document_id
- `family_entitlement` — id, family_id, type (land | house | job | annuity | transport
  | subsistence), status, due_on, delivered_on, document_id

**Origination**
- `sia_record` — id, project_id, status (not_started | commissioned | hearing_held |
  accepted | rejected), agency, hearing_date, venue, attendance_count,
  report_document_id, minutes_document_id

**Objections & grievances (one table, two types)**
- `objection` — id, type (objection | grievance | dispute), parcel_id, project_id,
  filed_by_person_id, filed_on, grounds, hearing_date, decision (accepted | rejected |
  partly_accepted | pending), decided_on, decision_document_id, effect_on_parcel

**Money**
- `award` — id, parcel_id, person_id, market_value, value_of_assets, solatium,
  other_amounts, interest, total_amount (generated), awarded_on, award_document_id
- `fund_deposit` — id, project_id, requiring_body_id, amount, deposited_on, reference
- `payment` — id, award_id, amount, paid_on, mode, external_ref (PFMS), status
  (pending | initiated | paid | failed)

**Closure**
- `possession` — id, parcel_id, taken_on, handed_over_to_body_id, document_id
- `mutation_request` — id, parcel_id, ulpin, adapter, sent_on, external_ref,
  status, response_payload jsonb

**Cross-cutting**
- `document` — id, entity_type, entity_id, kind, version, storage_path, sha256,
  uploaded_by, uploaded_at, supersedes_document_id  (versioned, never overwritten)
- `audit_log` — id, table_name, row_id, action, actor_id, at, reason, before jsonb, after jsonb
- `app_user`, `role`, `user_scope` (state / district scoping for row-level access)
- `alert_rule`, `alert_instance`

---

## 5. THE 17 STAGES AND WHAT TO DO WITH EACH

BUILD = working endpoints + tests. MODEL = valid stage rows in config, no endpoints.
ALERT = a rule in the alert engine, no dedicated endpoints.

| # | Stage | Statute ref | Do |
|---|---|---|---|
| 1 | Project proposal & land plan | — | BUILD |
| 2 | Consent (70% PPP / 80% private) | Sec 2(2) | BUILD |
| 3 | Social Impact Assessment + public hearing | Sec 4–5 | BUILD |
| 4 | Preliminary notification | Sec 11 / NH 3A | **BUILD — critical** |
| 5 | Objections & hearing (60-day window) | Sec 15 / NH 3C | BUILD |
| 6 | Field survey & ownership verification | Sec 12 | BUILD |
| 7 | R&R scheme — family census | Sec 16–18 | **BUILD — critical** |
| 8 | Declaration (12 months after stage 4, else rescinded) | Sec 19 / NH 3D | **BUILD — critical** |
| 9 | Notice to interested persons & claims | Sec 21–22 | MODEL |
| 10 | Award (12 months after stage 8, else lapses) | Sec 23 / NH 3G | **BUILD — critical** |
| 11a | Fund deposit by requiring body | — | BUILD |
| 11b | Compensation disbursement | Sec 26–30 / NH 3H | **BUILD — critical** |
| 12 | Possession & handover | Sec 38 / NH 3E | BUILD |
| 13 | Mutation push to land records | — | BUILD (stub adapter) |
| 14 | R&R execution & monitoring | — | MODEL (build if time) |
| 15 | Grievances & disputes | Sec 64 / NH 3G(5) | BUILD (reuses stage 5 table) |
| 16 | Unused land return after 5 years | Sec 101 | ALERT |
| 17 | De-notification & lapse | Sec 19 / 25 / 24 | ALERT |

Compensation formula for stage 10: market value + value of attached assets
+ 100% solatium + applicable interest. Put it in ONE pure function with unit tests.

---

## 6. ALERT RULES (stage 16, 17 and the risk score)

Implement as data-driven rules evaluated on a schedule, writing `alert_instance` rows:
- Section 19 declaration due within N days of the Section 11 event → warn; overdue → breach
- Section 25 award due within N days of the Section 19 event → warn; overdue → breach
- Section 101: possession taken > 5 years ago and parcel still unutilised → warn
- Award passed but no matching `fund_deposit` → blocked
- Award passed but payment status != paid after N days → blocked
- Open objections on a parcel past its hearing date → attention

Also expose a **rules-based risk score** per project (red / amber / green) derived from
the above. Do NOT build an ML model that trains on our own seed data — it would be
circular and indefensible. If a predictive endpoint is wanted, keep it clearly separate
and labelled as demonstrated on synthetic data.

---

## 7. WORK ORDER — one phase at a time, stop at each checkpoint

**Phase 1 — Foundation.** Docker + Alembic baseline, `app_user`/`role`/`user_scope`
with role-based access enforced at the query layer, generic `audit_log` trigger,
`document` table with versioning, `statute` + `stage_definition` seeded with BOTH
RFCTLARR and NH Act sequences, `requiring_body`, `project`, `parcel` with dual PostGIS
geometry. Duplicate-detection on project create.
→ CHECKPOINT: migrations run clean, tests pass, OpenAPI published.

**Phase 2 — The case engine.** `statutory_event`, one config-driven stage transition
service, deadline computation from `stage_definition`. Endpoints for stages 4 and 8.
→ CHECKPOINT: I can walk a parcel from proposal to declaration via the API, and the
computed deadline is correct. Unit tests on the date arithmetic are mandatory.

**Phase 3 — People and money.** `interested_person`, `parcel_interest`,
`affected_family`, `family_entitlement`, consent (stage 2), `award` (stage 10),
`fund_deposit` (11a), `payment` (11b).
→ CHECKPOINT: award computed per person per parcel, assessed-vs-paid queryable.

**Phase 4 — The rest of the lifecycle.** SIA (stage 3), objections/grievances
(stages 5 and 15), possession (12), mutation stub (13) behind `LandRecordAdapter`.
→ CHECKPOINT: full lifecycle walkable end to end on one parcel.

**Phase 5 — Read APIs.** Dashboard aggregates (area notified vs acquired, compensation
assessed vs paid, affected vs displaced families, possession status, R&R progress,
timeline adherence), filterable by state / district / project / sector. Map endpoint
returning GeoJSON FeatureCollections for parcels, with both geometries and the
discrepancy flag.
→ CHECKPOINT: every dashboard figure is a SQL aggregate. Prove it — no stored totals.

**Phase 6 — Alerts and risk score** per section 6.
→ CHECKPOINT: a seeded overdue project produces a visible breach alert.

**Phase 7 — Citizen view.** Unauthenticated read-only lookup by ULPIN or survey number
returning stage, award amount, payment status and objection outcome. Expose only what
is already published in the gazette. No personal contact details, no bank references.

---

## 8. RULES OF ENGAGEMENT

- **Stop at every checkpoint.** Report what you built, what you skipped and why.
- **Alembic for every schema change.** No `create_all()`. No destructive migrations.
- **One transition function.** All stage movement goes through it. No stage logic in routers.
- **Routers stay thin.** Business logic in services, DB access in repositories.
- **Tests are mandatory** for: the deadline arithmetic, the compensation formula, the
  audit trigger, and role scoping. Everything else is optional.
- **Do not write seed data.** A separate pair owns realistic seed data. Provide a
  documented loader interface and a tiny fixture set for tests only.
- **Do not touch frontend code.** Keep `/openapi.json` accurate and endpoint names
  stable; that is the contract with the frontend pair.
- **No new dependencies** without asking first.
- **Commit per logical unit** with a clear message. Never one giant commit per phase.
- **If something in this brief is ambiguous or looks wrong, ask. Do not guess.**

## 9. EXPLICIT NON-GOALS

Real PFMS integration. Real land-records API integration. Real e-Gazette publication.
An ML forecasting model. Multi-tenancy. Cloud deployment. i18n. Mobile native app.
Stage 9 and stage 14 endpoints. Anything not listed in section 7.
