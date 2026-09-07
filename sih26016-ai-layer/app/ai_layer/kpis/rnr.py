from sqlalchemy import func

from db.models import RnR, RnRStatus


def compute_rnr(session, case_ids: list[int]) -> dict:
    """KPI 4 — rehabilitation and resettlement progress, in people.

    Kept entirely apart from compensation. This measures housing and
    livelihood support for displaced households; compensation.py measures
    money for land. Never add the two, and never report one as a proxy for
    the other.
    """
    counts = {status: 0 for status in RnRStatus}
    if case_ids:
        rows = (
            session.query(RnR.status, func.count())
            .filter(RnR.case_id.in_(case_ids))
            .group_by(RnR.status)
            .all()
        )
        for status, count in rows:
            counts[status] = count

    return {
        "rnr_entitled_count": counts[RnRStatus.entitled],
        "rnr_in_progress_count": counts[RnRStatus.in_progress],
        "rnr_completed_count": counts[RnRStatus.completed],
    }
