from dataclasses import dataclass, field


@dataclass
class Alert:
    case_id: int
    rule: str
    severity: str  # "warning" | "critical"
    message: str
    detected_on: str  # ISO date, e.g. "2026-08-25"
    details: dict = field(default_factory=dict)

    def to_dict(self) -> dict:
        return {
            "case_id": self.case_id,
            "rule": self.rule,
            "severity": self.severity,
            "message": self.message,
            "detected_on": self.detected_on,
            "details": self.details,
        }
