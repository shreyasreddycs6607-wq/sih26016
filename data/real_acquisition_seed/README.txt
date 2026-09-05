BhoomiMitra real-public acquisition seed
===========================================

This package contains only data extracted/derived from identified public sources.
It is NOT a complete census of Karnataka land acquisition.

Important:
- Blank compensation_awarded / compensation_paid means the cited public source did not establish that value.
- Do not replace blanks with estimates and present them as official.
- owner_name_public contains names exactly as published in the cited acquisition schedules.
- Phone numbers, Aadhaar, bank details and other sensitive personal data are intentionally excluded.
- Duplicate survey numbers in a gazette are retained as separate parcel rows because the schedule itself lists them separately.
- area_acres is a mathematical conversion from the published hectares, not a separately published figure.

Files:
1. projects.csv - project-level facts
2. parcels.csv - parcel/survey/owner rows from acquisition schedules
3. sources.csv - provenance for each dataset group
