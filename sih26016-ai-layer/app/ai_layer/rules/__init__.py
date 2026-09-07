"""Rule registry and runner.

Each rule module exposes a single function, registered below, with the
signature: (cases: list[dict], as_of: date) -> list[Alert]. A rule is pure —
it never touches the database and never prints, and it takes "today" as an
explicit argument rather than calling date.today() itself, so the same
input always gives the same output (CLAUDE.md's determinism requirement).
This module is the only thing that calls each rule and the only thing that
would ever write results anywhere.
"""

import logging
from datetime import date

from app.ai_layer.constants import ANCHOR_DATE
from app.ai_layer.rules.award_unpaid import award_unpaid
from app.ai_layer.rules.case_stalled import case_stalled
from app.ai_layer.rules.document_missing import document_missing
from app.ai_layer.rules.objection_unanswered import objection_unanswered
from app.ai_layer.rules.possession_before_rnr import possession_before_rnr

logger = logging.getLogger(__name__)

REGISTRY = {
    "case_stalled": case_stalled,
    "document_missing": document_missing,
    "objection_unanswered": objection_unanswered,
    "award_unpaid": award_unpaid,
    "possession_before_rnr": possession_before_rnr,
}


def run_all_rules(cases: list[dict], as_of: date = ANCHOR_DATE) -> list[dict]:
    """Run every registered rule against the given cases.

    One rule raising an error is caught and skipped so the rest still run —
    a broken rule must never take the dashboard down mid-demo.
    Returns a flat list of alert dicts, each matching the shape in CLAUDE.md.
    """
    alerts: list[dict] = []
    for rule_name, rule_fn in REGISTRY.items():
        try:
            alerts.extend(alert.to_dict() for alert in rule_fn(cases, as_of))
        except Exception:  # noqa: BLE001 - isolate any single rule's failure
            # Logged, not printed: Backend imports this into their server
            # process, and a stray print in library code lands in their logs
            # as an unattributed line with no level and no traceback.
            logger.exception("rule '%s' failed and was skipped", rule_name)
    return alerts
