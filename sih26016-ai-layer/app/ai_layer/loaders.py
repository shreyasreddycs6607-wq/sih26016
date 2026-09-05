"""Turns database rows into the plain case dicts the rules consume.

This exists so the query lives next to the rules that define what it must
contain. Backend calls load_cases(session) and hands the result straight to
run_all_rules() — if they rebuilt this query in their own repo it would
drift from the rules the moment either side changed.

Loads everything in a fixed number of queries regardless of case count,
rather than walking relationships per case.
"""

from collections import defaultdict

from db.models import Case, Compensation, Document, Objection, RequiredDocument, RnR


def load_cases(session) -> list[dict]:
    rnr_statuses = defaultdict(list)
    for case_id, status in session.query(RnR.case_id, RnR.status).all():
        rnr_statuses[case_id].append(status.value)

    document_types = defaultdict(list)
    for case_id, doc_type in session.query(Document.case_id, Document.doc_type).all():
        document_types[case_id].append(doc_type.value)

    required_by_stage = defaultdict(list)
    for stage, doc_type in session.query(RequiredDocument.stage, RequiredDocument.doc_type).all():
        required_by_stage[stage].append(doc_type.value)

    objections = defaultdict(list)
    for row in session.query(Objection.id, Objection.case_id, Objection.status, Objection.filed_on).all():
        objections[row.case_id].append(
            {"id": row.id, "status": row.status.value, "filed_on": row.filed_on.isoformat()}
        )

    compensations = defaultdict(list)
    comp_columns = session.query(
        Compensation.case_id,
        Compensation.status,
        Compensation.amount_awarded,
        Compensation.amount_paid,
        Compensation.awarded_on,
    )
    for row in comp_columns.all():
        compensations[row.case_id].append(
            {
                "status": row.status.value,
                "amount_awarded": row.amount_awarded,
                "amount_paid": row.amount_paid,
                "awarded_on": row.awarded_on.isoformat(),
            }
        )

    cases = []
    for case in session.query(Case).order_by(Case.id).all():
        cases.append(
            {
                "id": case.id,
                "case_number": case.case_number,
                "stage": case.stage.value,
                "stage_changed_at": case.stage_changed_at.isoformat(),
                "rnr_statuses": rnr_statuses.get(case.id, []),
                "document_types": document_types.get(case.id, []),
                "required_document_types": required_by_stage.get(case.stage, []),
                "objections": objections.get(case.id, []),
                "compensations": compensations.get(case.id, []),
            }
        )
    return cases
