from datetime import date

from app.ai_layer.constants import OBJECTION_RESPONSE_DAYS
from app.ai_layer.rules.base import Alert


def objection_unanswered(cases: list[dict], as_of: date) -> list[Alert]:
    """Fires per objection left open past the response window.

    Always critical: an objection that was never answered can invalidate
    the acquisition itself, so this one carries legal weight rather than
    just being behind schedule.

    One alert per overdue objection, not per case — an officer needs to
    know which objection to answer, and a case can have several.
    """
    alerts = []
    for case in cases:
        for objection in case.get("objections", []):
            if objection["status"] != "open":
                continue
            days_open = (as_of - date.fromisoformat(objection["filed_on"])).days
            if days_open <= OBJECTION_RESPONSE_DAYS:
                continue
            alerts.append(
                Alert(
                    case_id=case["id"],
                    rule="objection_unanswered",
                    severity="critical",
                    message=f"Objection unanswered for {days_open} days, past the {OBJECTION_RESPONSE_DAYS}-day limit",
                    detected_on=as_of.isoformat(),
                    details={"objection_id": objection["id"], "days_open": days_open},
                )
            )
    return alerts
