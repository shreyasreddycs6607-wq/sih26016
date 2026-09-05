"""The five dashboard numbers named by the problem statement.

compute_kpis() is what Backend's dashboard route calls. It resolves the
scope filter once, then hands the resulting case ids to each of the five
calculations, so every number on the dashboard describes the same set of
cases.
"""

from app.ai_layer.kpis.area import compute_area
from app.ai_layer.kpis.compensation import compute_compensation
from app.ai_layer.kpis.families import compute_families
from app.ai_layer.kpis.possession import compute_possession
from app.ai_layer.kpis.rnr import compute_rnr
from db.models import Case, District, Project


def _scoped_case_ids(session, district_id: int | None, project_id: int | None) -> list[int]:
    """Resolve the filters to a concrete set of case ids.

    An unrecognised district or project raises rather than being ignored.
    Silently dropping a filter we do not understand would answer a narrow
    question with national totals — which, on a screen a role-restricted
    officer is looking at, means showing them data they may not be entitled
    to see. Failing loudly is the safe direction.
    """
    if district_id is not None and not session.get(District, district_id):
        raise ValueError(f"unknown district_id: {district_id}")
    if project_id is not None and not session.get(Project, project_id):
        raise ValueError(f"unknown project_id: {project_id}")

    query = session.query(Case.id)
    if district_id is not None:
        query = query.filter(Case.district_id == district_id)
    if project_id is not None:
        query = query.filter(Case.project_id == project_id)
    return [case_id for (case_id,) in query.all()]


def compute_kpis(session, district_id: int | None = None, project_id: int | None = None) -> dict:
    """Return all five dashboard numbers for the given scope.

    Pass no filters for national totals, or a district and/or project id to
    narrow. Whoever is allowed to ask for which scope is Backend's call —
    this function computes whatever it is asked for and enforces nothing.
    """
    case_ids = _scoped_case_ids(session, district_id, project_id)

    return {
        "scope": {
            "district_id": district_id,
            "project_id": project_id,
            "case_count": len(case_ids),
        },
        **compute_area(session, case_ids),
        **compute_compensation(session, case_ids),
        **compute_families(session, case_ids),
        **compute_rnr(session, case_ids),
        **compute_possession(session, case_ids),
    }
