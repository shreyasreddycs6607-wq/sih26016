"""Prints the five dashboard numbers, then independently re-derives them in
plain Python and asserts the two agree.

A KPI that runs without erroring is not a KPI that is correct — these are
the numbers a judge reads off the screen, so each one is checked against a
second calculation that shares no SQL with the first.
"""

from app.ai_layer.kpis import compute_kpis
from db.base import session_scope
from db.models import AffectedFamily, Case, Compensation, District, Parcel, ParcelStatus, Project, RnR


def _check(label: str, actual, expected) -> bool:
    ok = actual == expected
    print(f"  {'PASS' if ok else 'FAIL'}  {label:<38} kpi={actual!r:<14} recomputed={expected!r}")
    return ok


if __name__ == "__main__":
    with session_scope() as session:
        national = compute_kpis(session)

        print("National totals")
        for key, value in national.items():
            if key != "scope":
                print(f"  {key:<38} {value:,}" if isinstance(value, (int, float)) else f"  {key}: {value}")
        print(f"\n  cases in scope: {national['scope']['case_count']}\n")

        print("Cross-check against independently recomputed values")
        parcels = session.query(Parcel).all()
        comps = session.query(Compensation).all()
        families = session.query(AffectedFamily).all()
        rnrs = session.query(RnR).all()

        results = [
            _check("area_notified_ha", national["area_notified_ha"],
                   round(sum(p.area_ha for p in parcels), 4)),
            _check("area_acquired_ha", national["area_acquired_ha"],
                   round(sum(p.area_ha for p in parcels if p.status != ParcelStatus.notified), 4)),
            _check("compensation_awarded_total", national["compensation_awarded_total"],
                   sum(c.amount_awarded for c in comps)),
            _check("compensation_paid_total", national["compensation_paid_total"],
                   sum(c.amount_paid for c in comps)),
            _check("compensation_pending_total", national["compensation_pending_total"],
                   sum(c.amount_awarded - c.amount_paid for c in comps)),
            _check("affected_families_count", national["affected_families_count"],
                   len(families)),
            _check("rnr_entitled_count", national["rnr_entitled_count"],
                   sum(1 for r in rnrs if r.status.value == "entitled")),
            _check("rnr_in_progress_count", national["rnr_in_progress_count"],
                   sum(1 for r in rnrs if r.status.value == "in_progress")),
            _check("rnr_completed_count", national["rnr_completed_count"],
                   sum(1 for r in rnrs if r.status.value == "completed")),
            _check("possession_taken_count", national["possession_taken_count"],
                   sum(1 for p in parcels if p.status == ParcelStatus.possession_taken)),
            _check("possession_pending_count", national["possession_pending_count"],
                   sum(1 for p in parcels if p.status != ParcelStatus.possession_taken)),
        ]

        print("\nFilters actually narrow, and district totals sum to the national one")
        district_ids = [d.id for d in session.query(District).order_by(District.id).all()]
        per_district = [compute_kpis(session, district_id=d_id) for d_id in district_ids]
        for d_id, kpi in zip(district_ids, per_district):
            print(f"  district {d_id}: {kpi['scope']['case_count']:>3} cases, "
                  f"{kpi['area_notified_ha']:>10,.4f} ha notified, "
                  f"{kpi['affected_families_count']:>4} families")

        results.append(_check(
            "sum(district cases) == national",
            sum(k["scope"]["case_count"] for k in per_district),
            national["scope"]["case_count"]))
        results.append(_check(
            "sum(district families) == national",
            sum(k["affected_families_count"] for k in per_district),
            national["affected_families_count"]))
        results.append(_check(
            "sum(district compensation) == national",
            sum(k["compensation_awarded_total"] for k in per_district),
            national["compensation_awarded_total"]))

        project_id = session.query(Project.id).order_by(Project.id).first()[0]
        scoped = compute_kpis(session, project_id=project_id)
        results.append(_check(
            f"project {project_id} narrower than national",
            scoped["scope"]["case_count"] < national["scope"]["case_count"], True))

        print("\nUnknown filters must raise, never silently widen to national totals")
        for bad in ({"district_id": 9999}, {"project_id": 9999}):
            try:
                compute_kpis(session, **bad)
                results.append(_check(f"rejects {bad}", "no error", "ValueError"))
            except ValueError as exc:
                results.append(_check(f"rejects {bad}", "ValueError", "ValueError"))

        print(f"\n{sum(results)}/{len(results)} checks passed.")
