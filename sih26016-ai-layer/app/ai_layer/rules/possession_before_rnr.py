from datetime import date

from app.ai_layer.rules.base import Alert

POSSESSION_STAGES = {"possession", "monitoring"}


def possession_before_rnr(cases: list[dict], as_of: date) -> list[Alert]:
    alerts = []
    for case in cases:
        if case["stage"] not in POSSESSION_STAGES:
            continue
        rnr_statuses = case.get("rnr_statuses", [])
        incomplete = [s for s in rnr_statuses if s != "completed"]
        if not rnr_statuses:
            message = "Possession taken but no R&R records exist for this case"
        elif incomplete:
            message = f"Possession taken while R&R is incomplete for {len(incomplete)} of {len(rnr_statuses)} people"
        else:
            continue
        alerts.append(
            Alert(
                case_id=case["id"],
                rule="possession_before_rnr",
                severity="critical",
                message=message,
                detected_on=as_of.isoformat(),
                details={"incomplete_rnr_count": len(incomplete), "total_rnr_records": len(rnr_statuses)},
            )
        )
    return alerts
