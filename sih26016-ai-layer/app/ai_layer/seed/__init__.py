"""run_seed() — the one command that wipes and regenerates the whole
database. Fixed random seed (see constants.RANDOM_SEED), so running this
twice produces identical data both times."""

import random
from urllib.parse import urlparse

from sqlalchemy import text

from app.ai_layer import constants as c
from app.ai_layer.seed.anomalies import apply_anomalies
from app.ai_layer.seed.generators import (
    generate_affected_families,
    generate_cases,
    generate_compensation_and_rnr,
    generate_districts,
    generate_documents,
    generate_objections,
    generate_parcels,
    generate_people,
    generate_projects,
    generate_required_documents,
    generate_villages,
)
from db.base import DATABASE_URL, create_all, session_scope
from db.models import (
    AffectedFamily,
    Case,
    Compensation,
    District,
    Document,
    Objection,
    Parcel,
    Person,
    Project,
    RequiredDocument,
    RnR,
    Village,
)

LOCAL_HOSTS = {"localhost", "127.0.0.1", "::1", "db", ""}


def _assert_local_database(allow_remote: bool) -> None:
    """Refuse to wipe a database that is not on this machine.

    run_seed() deletes every row in every table. Once we start pointing
    DATABASE_URL at Backend's shared database, a reflexive re-run of the
    seed would destroy their data mid-integration. Local databases are
    ours to wipe freely; anything else has to be asked for explicitly.
    """
    if allow_remote:
        return
    host = (urlparse(DATABASE_URL).hostname or "").lower()
    if host not in LOCAL_HOSTS:
        raise RuntimeError(
            f"Refusing to wipe and reseed the database at host '{host}': it is not local.\n"
            f"run_seed() deletes every row in every table. If you really mean to reseed "
            f"this database, re-run with --allow-remote."
        )


WIPE_ORDER = (
    Objection,
    Document,
    RequiredDocument,
    AffectedFamily,
    RnR,
    Compensation,
    Parcel,
    Case,
    Person,
    Project,
    Village,
    District,
)


def _wipe(session):
    """Empty every table AND reset its id sequence.

    A plain DELETE leaves Postgres' sequences where they were, so each
    reseed hands out higher primary keys than the last — the same case
    comes back as id 13, then 157, then 209. That silently breaks anything
    that remembers an id across a reseed: deck screenshots, a bookmarked
    /cases/13, the case number someone wrote on a sticky note for the demo.
    TRUNCATE ... RESTART IDENTITY puts the sequences back to 1, so a
    regenerated database is identical down to its keys.

    The table names are interpolated from our own model classes, never from
    input — SQL identifiers cannot be bound parameters, so this is the only
    way to write it, and the values are ours.
    """
    tables = ", ".join(model.__tablename__ for model in WIPE_ORDER)
    session.execute(text(f"TRUNCATE TABLE {tables} RESTART IDENTITY CASCADE"))


def run_seed(rebuild: bool = False, allow_remote: bool = False) -> dict:
    _assert_local_database(allow_remote)
    create_all(rebuild=rebuild)
    rng = random.Random(c.RANDOM_SEED)

    with session_scope() as session:
        _wipe(session)

        districts = generate_districts(session)
        villages = generate_villages(session, districts)
        projects = generate_projects(session, districts)
        people = generate_people(session, villages, rng)
        cases = generate_cases(session, projects, districts, villages, rng)
        owners_by_case, area_by_case_owner = generate_parcels(session, cases, people, districts, rng)
        landless_by_case = generate_affected_families(session, cases, owners_by_case, people, rng)
        generate_compensation_and_rnr(session, cases, owners_by_case, area_by_case_owner, landless_by_case, rng)
        generate_required_documents(session)
        generate_documents(session, cases, rng)
        generate_objections(session, cases, people, rng)

        anomaly_summary = apply_anomalies(session, cases, rng)

        summary = {
            "districts": len(districts),
            "villages": len(villages),
            "projects": len(projects),
            "people": len(people),
            "cases": len(cases),
            "parcels": session.query(Parcel).count(),
            "compensation_records": session.query(Compensation).count(),
            "rnr_records": session.query(RnR).count(),
            "affected_families": session.query(AffectedFamily).count(),
            "required_document_rules": session.query(RequiredDocument).count(),
            "documents": session.query(Document).count(),
            "objections": session.query(Objection).count(),
            **anomaly_summary,
        }

    return summary
