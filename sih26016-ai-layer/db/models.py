import enum
from datetime import date

from geoalchemy2 import Geometry
from sqlalchemy import (
    Boolean,
    Date,
    Enum,
    Float,
    ForeignKey,
    Integer,
    String,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from db.base import Base


class CaseStage(str, enum.Enum):
    preliminary_notification = "preliminary_notification"
    social_impact_assessment = "social_impact_assessment"
    verification = "verification"
    objection_period = "objection_period"
    declaration = "declaration"
    award = "award"
    rnr = "rnr"
    possession = "possession"
    monitoring = "monitoring"


class CompensationStatus(str, enum.Enum):
    pending = "pending"
    paid = "paid"


class RnRStatus(str, enum.Enum):
    entitled = "entitled"
    in_progress = "in_progress"
    completed = "completed"


class DocumentType(str, enum.Enum):
    notification_gazette = "notification_gazette"
    sia_report = "sia_report"
    survey_report = "survey_report"
    ownership_record = "ownership_record"
    objection_register = "objection_register"
    declaration_gazette = "declaration_gazette"
    award_order = "award_order"
    rnr_entitlement_list = "rnr_entitlement_list"
    possession_certificate = "possession_certificate"


class ObjectionStatus(str, enum.Enum):
    open = "open"
    resolved = "resolved"


class ParcelStatus(str, enum.Enum):
    """Where an individual parcel has reached. Tracked per parcel, not per
    case, because KPI 1 (area notified vs acquired) and KPI 5 (possession,
    counted in parcels) both need parcel-level granularity — parcels in one
    case do not all progress together in practice."""

    notified = "notified"
    acquired = "acquired"
    possession_taken = "possession_taken"


class District(Base):
    __tablename__ = "districts"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String(80), nullable=False)
    state: Mapped[str] = mapped_column(String(80), nullable=False)

    villages: Mapped[list["Village"]] = relationship(back_populates="district")


class Village(Base):
    __tablename__ = "villages"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String(80), nullable=False)
    district_id: Mapped[int] = mapped_column(ForeignKey("districts.id"), nullable=False)

    district: Mapped[District] = relationship(back_populates="villages")


class Project(Base):
    __tablename__ = "projects"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String(160), nullable=False)
    requiring_body: Mapped[str] = mapped_column(String(120), nullable=False)
    district_id: Mapped[int] = mapped_column(ForeignKey("districts.id"), nullable=False)

    district: Mapped[District] = relationship()
    cases: Mapped[list["Case"]] = relationship(back_populates="project")


class Person(Base):
    __tablename__ = "people"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    village_id: Mapped[int] = mapped_column(ForeignKey("villages.id"), nullable=False)
    phone: Mapped[str | None] = mapped_column(String(15), nullable=True)
    has_land_title: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)

    village: Mapped[Village] = relationship()


class Case(Base):
    __tablename__ = "cases"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    case_number: Mapped[str] = mapped_column(String(40), nullable=False, unique=True)
    project_id: Mapped[int] = mapped_column(ForeignKey("projects.id"), nullable=False)
    district_id: Mapped[int] = mapped_column(ForeignKey("districts.id"), nullable=False)
    village_id: Mapped[int] = mapped_column(ForeignKey("villages.id"), nullable=False)
    stage: Mapped[CaseStage] = mapped_column(Enum(CaseStage, name="case_stage"), nullable=False)
    stage_changed_at: Mapped[date] = mapped_column(Date, nullable=False)
    created_at: Mapped[date] = mapped_column(Date, nullable=False)

    project: Mapped[Project] = relationship(back_populates="cases")
    district: Mapped[District] = relationship()
    village: Mapped[Village] = relationship()
    parcels: Mapped[list["Parcel"]] = relationship(back_populates="case")


class Parcel(Base):
    __tablename__ = "parcels"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    case_id: Mapped[int] = mapped_column(ForeignKey("cases.id"), nullable=False)
    survey_number: Mapped[str] = mapped_column(String(20), nullable=False)
    area_ha: Mapped[float] = mapped_column(Float, nullable=False)
    owner_id: Mapped[int] = mapped_column(ForeignKey("people.id"), nullable=False)
    status: Mapped[ParcelStatus] = mapped_column(Enum(ParcelStatus, name="parcel_status"), nullable=False)
    geom = mapped_column(Geometry(geometry_type="POINT", srid=4326), nullable=False)

    case: Mapped[Case] = relationship(back_populates="parcels")
    owner: Mapped[Person] = relationship()


class Compensation(Base):
    __tablename__ = "compensation"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    case_id: Mapped[int] = mapped_column(ForeignKey("cases.id"), nullable=False)
    person_id: Mapped[int] = mapped_column(ForeignKey("people.id"), nullable=False)
    amount_awarded: Mapped[int] = mapped_column(Integer, nullable=False)
    amount_paid: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    status: Mapped[CompensationStatus] = mapped_column(
        Enum(CompensationStatus, name="compensation_status"), nullable=False
    )
    awarded_on: Mapped[date] = mapped_column(Date, nullable=False)

    case: Mapped[Case] = relationship()
    person: Mapped[Person] = relationship()


class RnR(Base):
    __tablename__ = "rnr_records"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    case_id: Mapped[int] = mapped_column(ForeignKey("cases.id"), nullable=False)
    person_id: Mapped[int] = mapped_column(ForeignKey("people.id"), nullable=False)
    status: Mapped[RnRStatus] = mapped_column(Enum(RnRStatus, name="rnr_status"), nullable=False)
    updated_on: Mapped[date] = mapped_column(Date, nullable=False)

    case: Mapped[Case] = relationship()
    person: Mapped[Person] = relationship()


class AffectedFamily(Base):
    """One affected household per row, linked to the case that affects it.

    Needed because KPI 3 counts affected FAMILIES, which is broader than
    landowners — a tenant farmer's household is affected while owning no
    parcel. Owners are reachable through parcels, but landless households
    had no link to a case at all before this table, so early-stage cases
    would have counted zero affected families.

    Under the Act a household is identified as affected at the Social
    Impact Assessment stage, well before R&R entitlements are processed,
    so these rows exist from that stage onward — independently of the
    rnr_records table, which tracks entitlement progress separately.
    """

    __tablename__ = "affected_families"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    case_id: Mapped[int] = mapped_column(ForeignKey("cases.id"), nullable=False)
    person_id: Mapped[int] = mapped_column(ForeignKey("people.id"), nullable=False)
    is_landowner: Mapped[bool] = mapped_column(Boolean, nullable=False)

    case: Mapped[Case] = relationship()
    person: Mapped[Person] = relationship()


class RequiredDocument(Base):
    """Fixed lookup: which document types each stage requires.
    Flagged in the AI Layer Handbook as something to settle with Backend
    early — we own it ourselves for now. Seeded with fixed rows, not
    randomized like the rest of the data."""

    __tablename__ = "required_documents"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    stage: Mapped[CaseStage] = mapped_column(Enum(CaseStage, name="case_stage"), nullable=False)
    doc_type: Mapped[DocumentType] = mapped_column(Enum(DocumentType, name="document_type"), nullable=False)


class Document(Base):
    __tablename__ = "documents"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    case_id: Mapped[int] = mapped_column(ForeignKey("cases.id"), nullable=False)
    doc_type: Mapped[DocumentType] = mapped_column(Enum(DocumentType, name="document_type"), nullable=False)
    uploaded_on: Mapped[date] = mapped_column(Date, nullable=False)

    case: Mapped[Case] = relationship()


class Objection(Base):
    __tablename__ = "objections"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    case_id: Mapped[int] = mapped_column(ForeignKey("cases.id"), nullable=False)
    person_id: Mapped[int] = mapped_column(ForeignKey("people.id"), nullable=False)
    filed_on: Mapped[date] = mapped_column(Date, nullable=False)
    grounds: Mapped[str] = mapped_column(String(200), nullable=False)
    status: Mapped[ObjectionStatus] = mapped_column(Enum(ObjectionStatus, name="objection_status"), nullable=False)
    responded_on: Mapped[date | None] = mapped_column(Date, nullable=True)

    case: Mapped[Case] = relationship()
    person: Mapped[Person] = relationship()
