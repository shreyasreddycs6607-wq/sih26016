import os
from contextlib import contextmanager

from dotenv import load_dotenv
from sqlalchemy import create_engine, text
from sqlalchemy.orm import declarative_base, sessionmaker

load_dotenv()

DATABASE_URL = os.environ.get(
    "DATABASE_URL",
    "postgresql+psycopg2://ai_layer:ai_layer@localhost:5432/sih26016",
)

engine = create_engine(DATABASE_URL, future=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, future=True)
Base = declarative_base()


@contextmanager
def session_scope():
    session = SessionLocal()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


def create_all(rebuild: bool = False):
    """Create every table that doesn't exist yet.

    create_all only ever ADDS missing tables — it never alters a table whose
    columns have changed. So if the models change shape, an existing database
    silently keeps the old columns and the seed fails in confusing ways.
    Pass rebuild=True to drop every table first and rebuild the schema from
    scratch. That destroys all data, so it is opt-in only (the seed exposes
    it as `--rebuild`).
    """
    import db.models  # noqa: F401  (registers models on Base.metadata)

    with engine.connect() as conn:
        conn.execute(text("CREATE EXTENSION IF NOT EXISTS postgis"))
        conn.commit()
    if rebuild:
        Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
