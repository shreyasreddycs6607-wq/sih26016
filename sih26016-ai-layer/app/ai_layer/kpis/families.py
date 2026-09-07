from db.models import AffectedFamily


def compute_families(session, case_ids: list[int]) -> dict:
    """KPI 3 — affected families.

    Counts households, which is broader than landowners: a tenant farmer's
    household is affected while holding no title to any parcel. The
    landowner split is returned alongside because it is the number people
    reach for by mistake, and showing both makes the distinction visible
    rather than something we have to explain.
    """
    if not case_ids:
        return {
            "affected_families_count": 0,
            "affected_families_landowner_count": 0,
            "affected_families_landless_count": 0,
        }

    rows = session.query(AffectedFamily.is_landowner).filter(AffectedFamily.case_id.in_(case_ids)).all()
    landowner_count = sum(1 for (is_landowner,) in rows if is_landowner)

    return {
        "affected_families_count": len(rows),
        "affected_families_landowner_count": landowner_count,
        "affected_families_landless_count": len(rows) - landowner_count,
    }
