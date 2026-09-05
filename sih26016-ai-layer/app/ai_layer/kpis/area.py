from sqlalchemy import func

from db.models import Parcel, ParcelStatus

ACQUIRED_STATUSES = (ParcelStatus.acquired, ParcelStatus.possession_taken)


def compute_area(session, case_ids: list[int]) -> dict:
    """KPI 1 — hectares notified and acquired.

    Notified counts every parcel in scope: a parcel exists in the system
    because it was named in a preliminary notification. Acquired counts
    those that have cleared the award, possession included, since land
    already handed over is certainly acquired.
    """
    if not case_ids:
        return {"area_notified_ha": 0.0, "area_acquired_ha": 0.0}

    notified = session.query(func.coalesce(func.sum(Parcel.area_ha), 0.0)).filter(
        Parcel.case_id.in_(case_ids)
    ).scalar()
    acquired = session.query(func.coalesce(func.sum(Parcel.area_ha), 0.0)).filter(
        Parcel.case_id.in_(case_ids), Parcel.status.in_(ACQUIRED_STATUSES)
    ).scalar()

    return {
        "area_notified_ha": round(float(notified), 4),
        "area_acquired_ha": round(float(acquired), 4),
    }
