"""Runs all five rules against the seeded database and prints what fired,
grouped by rule and severity. This is how we check the alert spread is
demo-appropriate — a few warnings and one or two criticals, not forty of
everything (AI Layer Build Guide, Day 5 tuning)."""

from collections import Counter

from app.ai_layer.loaders import load_cases
from app.ai_layer.rules import REGISTRY, run_all_rules
from db.base import session_scope

if __name__ == "__main__":
    with session_scope() as session:
        cases = load_cases(session)

    alerts = run_all_rules(cases)

    print(f"Loaded {len(cases)} cases, {len(alerts)} alert(s) fired.\n")
    by_rule = Counter(alert["rule"] for alert in alerts)
    by_severity = Counter(alert["severity"] for alert in alerts)

    for rule_name in REGISTRY:
        print(f"  {rule_name}: {by_rule.get(rule_name, 0)}")
    print(f"\n  by severity: {dict(by_severity)}\n")

    for alert in alerts:
        print(f"  [{alert['severity']:8}] case {alert['case_id']:<5} {alert['rule']:<22} {alert['message']}")
