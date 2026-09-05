"""Deliberately breaks roughly ANOMALY_FRACTION of cases so each alert rule
has something real to catch (AI Layer Build Guide, Day 3). Runs last, after
every other generator, and mutates rows they already created rather than
inventing new ones.

Every query here is explicitly ordered by id. Postgres gives no row-order
guarantee without an ORDER BY, so an unordered .first() or .limit() could
pick different rows on different runs even with the random seed fixed —
which would quietly break the "regenerating produces identical data" rule
the whole demo depends on.
"""

import random
from datetime import timedelta

from app.ai_layer import constants as c
from db.models import (
    Case,
    CaseStage,
    Compensation,
    CompensationStatus,
    Document,
    Objection,
    ObjectionStatus,
    RnR,
    RnRStatus,
)

POSSESSION_STAGES = (CaseStage.possession, CaseStage.monitoring)


def apply_anomalies(session, cases: list[Case], rng: random.Random) -> dict:
    summary = {
        "cases_stalled_warning": 0,
        "cases_stalled_critical": 0,
        "documents_removed": 0,
        "objections_forced_open": 0,
        "awards_forced_unpaid": 0,
        "possession_before_rnr_forced": 0,
    }

    flawed_count = max(1, round(len(cases) * c.ANOMALY_FRACTION))
    flawed_cases = rng.sample(cases, min(flawed_count, len(cases)))

    # 1. Stalled cases — push stage_changed_at back past the warning
    #    threshold, and for some of them past the critical one too.
    stalled_total = c.ANOMALY_STALLED_CRITICAL_CASES + c.ANOMALY_STALLED_WARNING_CASES
    for i, case in enumerate(flawed_cases[:stalled_total]):
        if i < c.ANOMALY_STALLED_CRITICAL_CASES:
            days_back = c.STALLED_CRITICAL_DAYS + rng.randint(1, 15)
            summary["cases_stalled_critical"] += 1
        else:
            days_back = c.STALLED_DAYS + rng.randint(1, c.STALLED_CRITICAL_DAYS - c.STALLED_DAYS - 1)
            summary["cases_stalled_warning"] += 1
        case.stage_changed_at = c.ANCHOR_DATE - timedelta(days=days_back)

    # 2. Missing documents — delete one required document from a few of the
    #    flawed cases. One query for all of them rather than one per case.
    flawed_ids = [case.id for case in flawed_cases]
    docs = (
        session.query(Document)
        .filter(Document.case_id.in_(flawed_ids))
        .order_by(Document.case_id, Document.id)
        .all()
    )
    seen_cases = set()
    for doc in docs:
        if summary["documents_removed"] >= c.ANOMALY_DOCUMENTS_REMOVED:
            break
        if doc.case_id in seen_cases:
            continue
        seen_cases.add(doc.case_id)
        session.delete(doc)
        summary["documents_removed"] += 1

    # 3. Objections left open past the response window.
    stale_objections = (
        session.query(Objection)
        .filter(Objection.status == ObjectionStatus.resolved)
        .order_by(Objection.id)
        .limit(c.ANOMALY_OBJECTIONS_FORCED_OPEN)
        .all()
    )
    for objection in stale_objections:
        objection.status = ObjectionStatus.open
        objection.responded_on = None
        objection.filed_on = c.ANCHOR_DATE - timedelta(days=c.OBJECTION_RESPONSE_DAYS + rng.randint(1, 15))
        summary["objections_forced_open"] += 1

    # 4. Awards still unpaid past the payment window.
    stale_compensation = (
        session.query(Compensation)
        .filter(Compensation.status == CompensationStatus.pending)
        .order_by(Compensation.id)
        .limit(c.ANOMALY_AWARDS_FORCED_UNPAID)
        .all()
    )
    for compensation in stale_compensation:
        compensation.awarded_on = c.ANCHOR_DATE - timedelta(days=c.AWARD_PAYMENT_DAYS + rng.randint(1, 20))
        summary["awards_forced_unpaid"] += 1

    # 5. Possession taken before R&R is complete — the most domain-aware
    #    rule we have, so the seed must always contain at least one.
    possession_cases = sorted(
        (case for case in cases if case.stage in POSSESSION_STAGES),
        key=lambda case: case.id,
    )
    for target in rng.sample(possession_cases, min(c.ANOMALY_POSSESSION_BEFORE_RNR, len(possession_cases))):
        rnr = session.query(RnR).filter(RnR.case_id == target.id).order_by(RnR.id).first()
        if rnr:
            rnr.status = RnRStatus.in_progress
            summary["possession_before_rnr_forced"] += 1

    session.flush()
    return summary
