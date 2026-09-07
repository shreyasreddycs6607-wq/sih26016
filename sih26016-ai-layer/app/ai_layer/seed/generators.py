"""Builds every row for the seed data: districts, villages, projects,
cases, parcels, people, compensation, R&R, required-documents lookup,
documents and objections. Called by seed/__init__.py's run_seed(), in this
order, because later steps need earlier ones' output.

Deliberate anomalies are NOT built here — see seed/anomalies.py, which runs
last and mutates rows this file already created.
"""

import random
from datetime import timedelta

from geoalchemy2.shape import from_shape

from app.ai_layer import constants as c
from app.ai_layer.seed import reference as ref
from app.ai_layer.seed.geo import random_point_in_district
from db.models import (
    AffectedFamily,
    Case,
    CaseStage,
    Compensation,
    CompensationStatus,
    District,
    Document,
    DocumentType,
    Objection,
    ObjectionStatus,
    Parcel,
    ParcelStatus,
    Person,
    Project,
    RequiredDocument,
    RnR,
    RnRStatus,
    Village,
)

STAGE_ORDER = list(CaseStage)
STAGE_WEIGHTS = {
    CaseStage.preliminary_notification: 20,
    CaseStage.social_impact_assessment: 16,
    CaseStage.verification: 14,
    CaseStage.objection_period: 12,
    CaseStage.declaration: 10,
    CaseStage.award: 10,
    CaseStage.rnr: 8,
    CaseStage.possession: 6,
    CaseStage.monitoring: 4,
}
AWARD_INDEX = STAGE_ORDER.index(CaseStage.award)
RNR_INDEX = STAGE_ORDER.index(CaseStage.rnr)
SIA_INDEX = STAGE_ORDER.index(CaseStage.social_impact_assessment)
POSSESSION_INDEX = STAGE_ORDER.index(CaseStage.possession)


def _parcel_status_for(case: Case, rng: random.Random) -> ParcelStatus:
    """A parcel's status follows its case's stage, with a little lag —
    parcels within one case genuinely do not all clear together. Parcel
    status feeds no alert rule, so this variation cannot create rule noise
    the way loose case dates did."""
    stage_index = STAGE_ORDER.index(case.stage)
    if stage_index >= POSSESSION_INDEX:
        # Most parcels handed over, a few still being cleared.
        return rng.choices([ParcelStatus.possession_taken, ParcelStatus.acquired], weights=[85, 15], k=1)[0]
    if stage_index >= AWARD_INDEX:
        return rng.choices([ParcelStatus.acquired, ParcelStatus.notified], weights=[80, 20], k=1)[0]
    return ParcelStatus.notified


def generate_districts(session) -> dict[str, District]:
    districts = {}
    for name in c.DISTRICT_NAMES:
        d = District(name=name, state=c.STATE)
        session.add(d)
        districts[name] = d
    session.flush()
    return districts


def generate_villages(session, districts: dict[str, District]) -> dict[str, Village]:
    villages = {}
    for district_name, village_names in ref.DISTRICT_VILLAGES.items():
        for village_name in village_names:
            v = Village(name=village_name, district_id=districts[district_name].id)
            session.add(v)
            villages[village_name] = v
    session.flush()
    return villages


def generate_projects(session, districts: dict[str, District]) -> list[Project]:
    projects = []
    for p in ref.PROJECTS:
        proj = Project(
            name=p["name"],
            requiring_body=p["requiring_body"],
            district_id=districts[p["district_name"]].id,
        )
        session.add(proj)
        projects.append(proj)
    session.flush()
    return projects


def generate_people(session, villages: dict[str, Village], rng: random.Random) -> list[Person]:
    people = []
    village_list = list(villages.values())
    per_village = max(1, c.PERSON_COUNT_MIN // len(village_list) + 1)
    phone_seq = 0
    for village in village_list:
        for _ in range(per_village):
            is_male = rng.random() < 0.5
            first = rng.choice(ref.FIRST_NAMES_MALE if is_male else ref.FIRST_NAMES_FEMALE)
            last = rng.choice(ref.LAST_NAMES)
            has_land_title = rng.random() >= c.LANDLESS_AFFECTED_FRACTION
            phone_seq += 1
            person = Person(
                name=f"{first} {last}",
                village_id=village.id,
                phone=f"{c.FAKE_PHONE_PREFIX}{phone_seq:05d}",
                has_land_title=has_land_title,
            )
            session.add(person)
            people.append(person)
    session.flush()
    return people


def generate_cases(
    session,
    projects: list[Project],
    districts: dict[str, District],
    villages: dict[str, Village],
    rng: random.Random,
) -> list[Case]:
    total_cases = rng.randint(*c.CASE_COUNT_RANGE)
    stages = list(STAGE_WEIGHTS.keys())
    weights = list(STAGE_WEIGHTS.values())

    district_by_id = {d.id: name for name, d in districts.items()}
    villages_by_district_name = {
        district_name: [v for v in villages.values() if district_by_id[v.district_id] == district_name]
        for district_name in c.DISTRICT_NAMES
    }
    seq_by_district = {name: 0 for name in c.DISTRICT_NAMES}

    cases = []
    for _ in range(total_cases):
        project = rng.choice(projects)
        district_name = district_by_id[project.district_id]
        village = rng.choice(villages_by_district_name[district_name])
        stage = rng.choices(stages, weights=weights, k=1)[0]

        seq_by_district[district_name] += 1
        abbr = ref.DISTRICT_ABBR[district_name]
        case_number = f"KA/{abbr}/2026/{seq_by_district[district_name]:03d}"

        # Kept under STALLED_DAYS on purpose: the baseline data must not
        # accidentally trip case_stalled. Only anomalies.py should push
        # specific cases past that threshold.
        stage_changed_at = c.ANCHOR_DATE - timedelta(days=rng.randint(0, c.STALLED_DAYS - 1))
        created_at = stage_changed_at - timedelta(days=rng.randint(30, 300))

        case = Case(
            case_number=case_number,
            project_id=project.id,
            district_id=districts[district_name].id,
            village_id=village.id,
            stage=stage,
            stage_changed_at=stage_changed_at,
            created_at=created_at,
        )
        session.add(case)
        cases.append(case)
    session.flush()
    return cases


def generate_parcels(
    session,
    cases: list[Case],
    people: list[Person],
    districts: dict[str, District],
    rng: random.Random,
) -> tuple[dict[int, list[Person]], dict[tuple[int, int], float]]:
    """Returns two lookups for compensation/R&R to build on:
    - case.id -> list of unique owner Persons
    - (case.id, owner.id) -> total hectares that owner holds in that case,
      so compensation can be priced off land actually owned rather than a
      second, unrelated random number."""
    district_name_by_id = {d.id: name for name, d in districts.items()}

    landowners_by_village_id: dict[int, list[Person]] = {}
    for person in people:
        if person.has_land_title:
            landowners_by_village_id.setdefault(person.village_id, []).append(person)
    all_landowners = [p for p in people if p.has_land_title]

    total_parcels = rng.randint(*c.PARCEL_COUNT_RANGE)
    base = total_parcels // len(cases)
    remainder = total_parcels % len(cases)

    owners_by_case: dict[int, list[Person]] = {}
    area_by_case_owner: dict[tuple[int, int], float] = {}
    for i, case in enumerate(cases):
        parcel_count = max(1, base + (1 if i < remainder else 0))
        candidates = landowners_by_village_id.get(case.village_id) or all_landowners
        district_name = district_name_by_id[case.district_id]

        owners_this_case = set()
        for _ in range(parcel_count):
            owner = rng.choice(candidates)
            owners_this_case.add(owner.id)

            base_survey = rng.randint(1, 300)
            sub_survey = rng.randint(1, 6)
            survey_number = f"{base_survey}/{sub_survey}"
            if rng.random() < 0.4:
                survey_number += rng.choice("ABC")

            point = random_point_in_district(district_name, rng)
            area_ha = round(rng.uniform(*c.PARCEL_AREA_HA_RANGE), 4)

            parcel = Parcel(
                case_id=case.id,
                survey_number=survey_number,
                area_ha=area_ha,
                owner_id=owner.id,
                status=_parcel_status_for(case, rng),
                geom=from_shape(point, srid=4326),
            )
            session.add(parcel)

            key = (case.id, owner.id)
            area_by_case_owner[key] = round(area_by_case_owner.get(key, 0.0) + area_ha, 4)

        owners_by_case[case.id] = [p for p in candidates if p.id in owners_this_case]

    session.flush()
    return owners_by_case, area_by_case_owner


def generate_affected_families(
    session,
    cases: list[Case],
    owners_by_case: dict[int, list[Person]],
    people: list[Person],
    rng: random.Random,
) -> dict[int, list[Person]]:
    """One row per affected household per case. Returns case.id -> the
    landless households among them, which R&R then draws from, so both
    tables agree on who a case actually affects.

    Landowners are identified from land records at notification. Landless
    households — tenant farmers, labourers — are what the Social Impact
    Assessment exists to find, so they appear from that stage onward.
    """
    landless_by_village_id: dict[int, list[Person]] = {}
    for person in people:
        if not person.has_land_title:
            landless_by_village_id.setdefault(person.village_id, []).append(person)

    landless_by_case: dict[int, list[Person]] = {}
    for case in cases:
        stage_index = STAGE_ORDER.index(case.stage)

        for owner in owners_by_case.get(case.id, []):
            session.add(AffectedFamily(case_id=case.id, person_id=owner.id, is_landowner=True))

        landless_here: list[Person] = []
        if stage_index >= SIA_INDEX:
            pool = landless_by_village_id.get(case.village_id, [])
            sample_size = min(len(pool), rng.randint(2, 5))
            landless_here = rng.sample(pool, sample_size) if sample_size else []
            for person in landless_here:
                session.add(AffectedFamily(case_id=case.id, person_id=person.id, is_landowner=False))

        landless_by_case[case.id] = landless_here

    session.flush()
    return landless_by_case


def generate_compensation_and_rnr(
    session,
    cases: list[Case],
    owners_by_case: dict[int, list[Person]],
    area_by_case_owner: dict[tuple[int, int], float],
    landless_by_case: dict[int, list[Person]],
    rng: random.Random,
) -> None:
    for case in cases:
        stage_index = STAGE_ORDER.index(case.stage)
        owners = owners_by_case.get(case.id, [])

        if stage_index >= AWARD_INDEX:
            rate_per_ha = rng.randint(*c.COMPENSATION_RATE_PER_HA_RANGE)
            for owner in owners:
                # Priced off the land this owner actually holds in this
                # case, so compensation reconciles with the parcel areas
                # on screen instead of contradicting them.
                owned_ha = area_by_case_owner.get((case.id, owner.id), 0.0)
                amount_awarded = int(round(owned_ha * rate_per_ha))
                status = rng.choices(
                    [CompensationStatus.paid, CompensationStatus.pending], weights=[60, 40], k=1
                )[0]
                amount_paid = amount_awarded if status == CompensationStatus.paid else 0
                awarded_on = case.stage_changed_at - timedelta(days=rng.randint(0, 15))
                session.add(
                    Compensation(
                        case_id=case.id,
                        person_id=owner.id,
                        amount_awarded=amount_awarded,
                        amount_paid=amount_paid,
                        status=status,
                        awarded_on=awarded_on,
                    )
                )

        if stage_index >= RNR_INDEX:
            # Entitlements go to the households already identified as
            # affected, so rnr_records and affected_families never disagree.
            affected_landless = landless_by_case.get(case.id, [])
            # Cases already at possession/monitoring should, organically,
            # show R&R as done — the law requires it settled before
            # displacement. Leaving the baseline mostly "completed" here
            # means the possession_before_rnr rule only fires on cases
            # anomalies.py deliberately breaks, not on random noise.
            if case.stage in (CaseStage.possession, CaseStage.monitoring):
                status_choices, weights = [RnRStatus.completed, RnRStatus.in_progress], [98, 2]
            else:
                status_choices = [RnRStatus.entitled, RnRStatus.in_progress, RnRStatus.completed]
                weights = [30, 40, 30]
            for person in owners + affected_landless:
                status = rng.choices(status_choices, weights=weights, k=1)[0]
                updated_on = case.stage_changed_at - timedelta(days=rng.randint(0, 10))
                session.add(
                    RnR(
                        case_id=case.id,
                        person_id=person.id,
                        status=status,
                        updated_on=updated_on,
                    )
                )

    session.flush()


def generate_required_documents(session) -> None:
    """Fixed lookup rows, not randomized — see db.models.RequiredDocument."""
    for stage_key, doc_types in ref.REQUIRED_DOCUMENTS.items():
        for doc_type in doc_types:
            session.add(RequiredDocument(stage=CaseStage(stage_key), doc_type=DocumentType(doc_type)))
    session.flush()


def generate_documents(session, cases: list[Case], rng: random.Random) -> None:
    """Every case gets its current stage's required documents, complete.
    Gaps are anomalies.py's job alone — if this function also dropped
    documents at random, document_missing would fire on cases nobody chose
    and the real anomalies would be lost in the noise."""
    for case in cases:
        for doc_type in ref.REQUIRED_DOCUMENTS.get(case.stage.value, []):
            uploaded_on = min(c.ANCHOR_DATE, case.stage_changed_at + timedelta(days=rng.randint(0, 5)))
            session.add(Document(case_id=case.id, doc_type=DocumentType(doc_type), uploaded_on=uploaded_on))
    session.flush()


def generate_objections(session, cases: list[Case], people: list[Person], rng: random.Random) -> None:
    eligible = [case for case in cases if STAGE_ORDER.index(case.stage) >= STAGE_ORDER.index(CaseStage.objection_period)]
    people_by_village_id: dict[int, list[Person]] = {}
    for person in people:
        people_by_village_id.setdefault(person.village_id, []).append(person)

    total_objections = rng.randint(*c.OBJECTION_COUNT_RANGE)
    if not eligible:
        return

    for _ in range(total_objections):
        case = rng.choice(eligible)
        candidates = people_by_village_id.get(case.village_id) or people
        person = rng.choice(candidates)

        filed_on = min(c.ANCHOR_DATE, case.stage_changed_at + timedelta(days=rng.randint(0, 10)))
        status = rng.choices([ObjectionStatus.resolved, ObjectionStatus.open], weights=[70, 30], k=1)[0]
        responded_on = None
        if status == ObjectionStatus.resolved:
            responded_on = min(c.ANCHOR_DATE, filed_on + timedelta(days=rng.randint(3, 20)))

        session.add(
            Objection(
                case_id=case.id,
                person_id=person.id,
                filed_on=filed_on,
                grounds=rng.choice(ref.OBJECTION_GROUNDS),
                status=status,
                responded_on=responded_on,
            )
        )
    session.flush()
