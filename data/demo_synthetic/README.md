# BhoomiMitra synthetic demo layer

This folder is **DEMO/SYNTHETIC ONLY**. These records are intentionally fictional and are not claims about any real landowner, award, payment, objection, R&R entitlement, document, alert, or officer action.

Use this layer to populate the BhoomiMitra UI and exercise dashboard workflows around the real public-source records in `data/real_acquisition_seed/`.

## Rules
- Never present these values as government records.
- Names are explicitly synthetic.
- Compensation amounts are fabricated demo values.
- Case/stage dates are fabricated for UI testing.
- No Aadhaar, bank account, real phone number, or other sensitive personal data is included.
- The enum values mirror the backend contract in `sih26016-backend/app/core/enums.py`.
- IDs are stable string keys for import/mapping scripts; the backend can map them to database integer IDs during a seed run.

## Files
- `cases.csv` — synthetic case workflow records tied to public project IDs.
- `people.csv` — synthetic people/affected-family identities.
- `compensation.csv` — synthetic compensation examples.
- `rnr_records.csv` — synthetic rehabilitation/resettlement examples.
- `objections.csv` — synthetic objection workflow examples.
- `alerts.csv` — synthetic dashboard alerts.
- `documents.csv` — synthetic document metadata.
- `case_stage_history.csv` — synthetic stage timelines.
- `affected_families.csv` — synthetic consent/displacement records.

The real public-source layer remains separate so the demo can clearly distinguish verified public facts from fabricated UI data.
