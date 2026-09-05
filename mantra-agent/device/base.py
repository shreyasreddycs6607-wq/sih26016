"""The seam between the agent's HTTP surface and whatever fingerprint
hardware is actually attached.

Same shape as sih26016-backend/app/integrations: one Protocol, a mock that
implements it deterministically and says so everywhere, and a real adapter
selected by one config value. Adding real MFS100 support later, or a
different vendor's scanner entirely, is one new class and one registry
entry — main.py and backend_client.py never change.
"""

from typing import Protocol


class DeviceError(Exception):
    """Capture or match failed in a way the caller should show verbatim —
    "no finger presented", "capture timed out", "device not found". Never
    raised for a plain non-match; a non-match is a normal, expected outcome
    the caller decides what to do with, not a fault."""


class FingerprintDevice(Protocol):
    """A source of truth for exactly two operations: capture one template,
    and score how similar two templates are. Nothing here decides whether
    a score counts as a match — that threshold lives on the Bhoomimitra
    backend (app/routers/biometrics.py's MIN_FINGERPRINT_MATCH_SCORE), not
    here, so the same policy applies whichever device produced the score.
    """

    label: str  # shown in /health, e.g. "mock" or "mfs100"

    def health(self) -> dict:
        """A live, uncached connectivity check — {connected: bool, detail:
        str, device?: {...}} — never the cheaper "did construction succeed"
        check, since a device can go from attached to unattached at any
        point while this agent keeps running."""
        ...

    def capture_template(self, timeout_ms: int) -> str:
        """One fingerprint, base64-encoded, in whatever template format
        this device produces. Raises DeviceError on timeout, no finger, or
        a device-level failure — never returns an empty/placeholder
        string."""
        ...

    def match(self, template_a: str, template_b: str) -> int:
        """0-100000, Mantra's own documented range for MFS100MatchISO
        regardless of which device actually produced it — a mock or a
        different real device implementing this Protocol should scale its
        own notion of similarity onto the same range so
        MIN_FINGERPRINT_MATCH_SCORE means the same thing everywhere."""
        ...
