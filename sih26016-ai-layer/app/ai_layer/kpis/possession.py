from sqlalchemy import func

from db.models import Parcel, ParcelStatus


def compute_possession(session, case_ids: list[int]) -> dict:
    """KPI 5 — possession, counted in parcels rather than cases.

    A case is rarely all-or-nothing: some parcels are handed over while
    others are still being cleared, so counting whole cases would overstate
    or understate progress depending on which way you rounded.
    """
    if not case_ids:
        return {"possession_taken_count": 0, "possession_pending_count": 0}

    taken = session.query(func.count(Parcel.id)).filter(
        Parcel.case_id.in_(case_ids), Parcel.status == ParcelStatus.possession_taken
    ).scalar()
    total = session.query(func.count(Parcel.id)).filter(Parcel.case_id.in_(case_ids)).scalar()

    return {
        "possession_taken_count": int(taken),
        "possession_pending_count": int(total) - int(taken),
    }
