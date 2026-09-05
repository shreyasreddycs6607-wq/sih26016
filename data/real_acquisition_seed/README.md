# Verified public acquisition seed

This directory contains a curated, point-in-time public-source dataset for the BhoomiMitra SIH prototype.

## What is loaded into PostgreSQL

`python -m app.real_seed` imports:

- project-level acquisition records from `projects.csv`
- parcel-level public schedule records from `parcels.csv`
- publicly reported compensation milestones when explicitly present in the source data

The records are stored in the read-only `public_acquisition_records` table and exposed through:

- `GET /public-acquisitions`
- `GET /public-acquisitions/summary`

## Data integrity rules

- Public owner/interested-person names are retained only when they were explicitly listed in the public schedule.
- No Aadhaar numbers, bank details, private phone numbers or other sensitive identifiers are imported.
- Missing compensation remains NULL; the loader never estimates a payment.
- Missing geometry/ULPIN remains NULL; the loader never invents a map coordinate.
- Project and parcel extents are kept as separate record types so dashboard summaries do not double-count them.
- The source reference is retained with each record for provenance.

## Enabling the seed

For local/SIH development, set `REAL_SEED_ENABLED=true` in the backend `.env`. Production validation rejects this setting in production.

The seed is idempotent: re-running it does not create duplicate records.

## Scope

The dataset is not a claim that it represents every Karnataka acquisition. It is a verified public-source seed assembled from the sources documented in `sources.csv` and the corresponding project/parcel CSV records. New public notices can be added to the CSV layer and re-seeded.
