from datetime import date

from app.ai_layer.rules.base import Alert


def document_missing(cases: list[dict], as_of: date) -> list[Alert]:
    """Fires when a document the case's CURRENT stage requires is absent.

    Deliberately scoped to the current stage only. Checking every stage the
    case has already passed would flag historical gaps an officer can no
    longer act on, and the point of an alert is to name something that can
    be done today.
    """
    alerts = []
    for case in cases:
        required = set(case.get("required_document_types", []))
        present = set(case.get("document_types", []))
        missing = sorted(required - present)
        if not missing:
            continue
        alerts.append(
            Alert(
                case_id=case["id"],
                rule="document_missing",
                severity="warning",
                message=f"Missing {len(missing)} document(s) required at this stage: {', '.join(missing)}",
                detected_on=as_of.isoformat(),
                details={"missing_document_types": missing},
            )
        )
    return alerts
