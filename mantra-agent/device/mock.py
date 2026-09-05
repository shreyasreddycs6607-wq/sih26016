"""A stand-in for a physical MFS100 scanner.

Deterministic, offline, and labelled everywhere as simulated — the same
reasoning as sih26016-backend/app/integrations/mock.py's docstring: this
exists so the whole fingerprint login pipeline (agent capture -> backend
challenge/nonce -> match -> JWT) can be built, demoed and rehearsed today,
before real hardware is in the room, without pretending a scanner is
attached when it isn't.

**What it actually simulates.** Every capture returns the exact same fixed
template string. That makes "enroll once, then log in" work correctly end
to end — the enrolled template and every later captured template are
identical, so match() scores them as a clean match — while making it
obvious this is not measuring anything about a real finger: swap
MFS100_MODE=mock for MFS100_MODE=real and nothing else in this codebase
needs to change, but the security property this whole feature exists for
(a real biometric factor) does not exist until that swap happens.
"""

import time

# Not a real template in any sense a device SDK would recognise — just a
# fixed string every mock capture returns, so two captures always compare
# equal. Prefixed so it is unmistakable in a database dump or a log line.
_FIXED_TEMPLATE = "MOCK-MFS100-TEMPLATE-v1"


class MockFingerprintDevice:
    label = "mock"

    def health(self) -> dict:
        # Always "connected" — there is no hardware to lose contact with,
        # and pretending there's a chance of disconnection here would just
        # be a second way for this file to lie about simulating a scanner.
        return {"connected": True, "detail": "Mock device — no real scanner attached."}

    def capture_template(self, timeout_ms: int) -> str:
        # A small delay so the frontend's "Present your finger…" state is
        # visible for a moment rather than resolving instantly, which
        # would look like the capture never happened.
        time.sleep(min(0.6, timeout_ms / 1000))
        return _FIXED_TEMPLATE

    def match(self, template_a: str, template_b: str) -> int:
        # Comfortably above biometrics.MIN_FINGERPRINT_MATCH_SCORE (14000)
        # on an exact string match, comfortably below it otherwise — mirrors
        # a real matcher's all-or-nothing behaviour on two unrelated
        # fingers without pretending to model partial similarity.
        return 90_000 if template_a == template_b else 0
