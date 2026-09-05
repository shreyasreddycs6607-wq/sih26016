"""Every tunable number and every fixed decision for the AI Layer lives here.
Nothing in rules/, kpis/ or seed/ should hardcode a threshold, a seed value,
or a district name inline — import it from here instead."""

from datetime import date

# Fixed "today" for seed generation, so regenerating on a different real date
# still produces identical stage_changed_at / created_at values.
ANCHOR_DATE = date(2026, 8, 26)

# --- Alert rule thresholds ---
STALLED_DAYS = 10
STALLED_CRITICAL_DAYS = 20
OBJECTION_RESPONSE_DAYS = 21
AWARD_PAYMENT_DAYS = 30

# --- Sample data ---
RANDOM_SEED = 26016

STATE = "Karnataka"
DISTRICT_NAMES = [
    "Bengaluru Rural",
    "Tumakuru",
    "Ramanagara",
    "Kolar",
]

# Volume targets (Handbook, Deliverable 3).
# Project count is not a range — it is however many are listed in
# seed/reference.py PROJECTS, so it lives there rather than here.
CASE_COUNT_RANGE = (40, 60)
PARCEL_COUNT_RANGE = (200, 300)
PERSON_COUNT_MIN = 300
OBJECTION_COUNT_RANGE = (15, 25)

# Proportion of affected people who hold no land title (tenant farmers, labourers)
LANDLESS_AFFECTED_FRACTION = 0.3

# Phone numbers are issued sequentially from one obviously-fake block rather
# than sampled at random from the real Indian mobile range. A random
# 10-digit 9-series number is very likely to be somebody's actual number,
# and these end up on screens, in screenshots and in the deck.
FAKE_PHONE_PREFIX = "99999"

# --- Realism ranges ---
PARCEL_AREA_HA_RANGE = (0.05, 2.5)
COMPENSATION_RATE_PER_HA_RANGE = (1_500_000, 3_500_000)

# --- Deliberate anomalies ---
# How many cases get broken on purpose so each rule has something to catch.
# These are the numbers to tune on Day 5 to get a good spread on the demo
# dashboard — a few warnings and one or two criticals, not forty of each.
# The baseline data is generated so it never trips a rule by accident, so
# the alert counts below are exactly what the dashboard will show.
ANOMALY_FRACTION = 0.15
ANOMALY_STALLED_CRITICAL_CASES = 2
ANOMALY_STALLED_WARNING_CASES = 2
ANOMALY_DOCUMENTS_REMOVED = 3
ANOMALY_OBJECTIONS_FORCED_OPEN = 2
ANOMALY_AWARDS_FORCED_UNPAID = 1
ANOMALY_POSSESSION_BEFORE_RNR = 1
