from datetime import date

from app.ai_layer.constants import AWARD_PAYMENT_DAYS
from app.ai_layer.rules.base import Alert


def award_unpaid(cases: list[dict], as_of: date) -> list[Alert]:
    """Fires when an award was made but compensation is still pending past
    the payment window.

    Aggregated to one alert per case, unlike objection_unanswered: the
    officer's action here is to chase one case's disbursement, and the
    beneficiary count plus the outstanding total say how urgent it is
    without naming anybody on a dashboard.
    """
    alerts = []
    for case in cases:
        overdue = [
            comp
            for comp in case.get("compensations", [])
            if comp["status"] == "pending"
            and (as_of - date.fromisoformat(comp["awarded_on"])).days > AWARD_PAYMENT_DAYS
        ]
        if not overdue:
            continue
        amount_pending = sum(comp["amount_awarded"] - comp["amount_paid"] for comp in overdue)
        longest_wait = max((as_of - date.fromisoformat(comp["awarded_on"])).days for comp in overdue)
        alerts.append(
            Alert(
                case_id=case["id"],
                rule="award_unpaid",
                severity="warning",
                message=(
                    f"Award unpaid for {len(overdue)} beneficiary(ies) after {longest_wait} days, "
                    f"Rs {amount_pending:,} outstanding"
                ),
                detected_on=as_of.isoformat(),
                details={
                    "beneficiary_count": len(overdue),
                    "amount_pending": amount_pending,
                    "days_since_award": longest_wait,
                },
            )
        )
    return alerts
