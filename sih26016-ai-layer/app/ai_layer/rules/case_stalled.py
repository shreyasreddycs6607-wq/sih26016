from datetime import date

from app.ai_layer.constants import STALLED_CRITICAL_DAYS, STALLED_DAYS
from app.ai_layer.rules.base import Alert


def case_stalled(cases: list[dict], as_of: date) -> list[Alert]:
    alerts = []
    for case in cases:
        stage_changed_at = date.fromisoformat(case["stage_changed_at"])
        days_stalled = (as_of - stage_changed_at).days
        if days_stalled < STALLED_DAYS:
            continue
        severity = "critical" if days_stalled >= STALLED_CRITICAL_DAYS else "warning"
        alerts.append(
            Alert(
                case_id=case["id"],
                rule="case_stalled",
                severity=severity,
                message=f"Stage unchanged for {days_stalled} days",
                detected_on=as_of.isoformat(),
                details={"days_stalled": days_stalled},
            )
        )
    return alerts
