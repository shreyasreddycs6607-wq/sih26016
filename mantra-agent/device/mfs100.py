"""The real Mantra MFS100 device, via its native SDK DLL.

**UNVERIFIED — nobody has run this against actual hardware yet.** Written
from public documentation and third-party integration writeups, not from
Mantra's own SDK headers or sample code, because neither was available when
this was written (see mantra-agent/README.md). Everything marked VERIFY
below is a specific, named thing to check against the real SDK zip Mantra
ships with the scanner — do not assume any of it is correct until it has
actually been run against the device.

What's confirmed from public documentation (Mantra's own published
guidance, cross-referenced against several independent integration
writeups):
- The SDK exposes fingerprint capture, ISO/ANSI-378 template extraction,
  and local template matching (MFS100MatchISO), distinct from the
  UIDAI/Aadhaar "RD Service" mode, which encrypts its output for UIDAI's
  servers and is unusable for local matching — see the module docstring
  in sih26016-backend/app/services/kiosk_auth.py for why that mode is the
  wrong one.
- MFS100MatchISO's score range is documented as 0-100000, with >=14000
  considered a match — this project's MIN_FINGERPRINT_MATCH_SCORE in
  biometrics.py already uses that figure.

What is NOT confirmed and is guessed at below:
- VERIFY: the exact DLL filename (MFS100.dll vs MFS100Dll.dll vs
  something version-specific) and whether it ships 32-bit, 64-bit, or
  both — this matters for whether this process needs to run as a 32-bit
  Python interpreter.
- VERIFY: every function's exact signature (argument types, order,
  calling convention — stdcall vs cdecl). The ctypes bindings below are a
  best-effort reconstruction of a typical fingerprint-SDK shape, not a
  transcription of a real header file.
- VERIFY: whether initialisation happens once per process or must be
  repeated per capture, and whether the device needs to be explicitly
  released/closed for a second capture to succeed.
"""

import base64
import ctypes

from device.base import DeviceError

# VERIFY: confirm this against the actual SDK's installed DLL filename.
_DLL_NAME = "MFS100.dll"


class MFS100Device:
    label = "mfs100"

    def __init__(self) -> None:
        try:
            # VERIFY: cdll vs windll (i.e. cdecl vs stdcall) — guessed as
            # windll here because most Windows device SDKs of this vintage
            # use __stdcall, but this is exactly the kind of thing that
            # fails loudly and immediately if wrong, which is the good
            # case: a wrong calling convention crashes on the first call
            # rather than silently corrupting a score.
            self._dll = ctypes.WinDLL(_DLL_NAME)
        except OSError as exc:
            raise DeviceError(
                f"Could not load {_DLL_NAME}. Is the Mantra MFS100 driver installed on "
                "this machine, and is the scanner plugged in?"
            ) from exc

        # VERIFY: real function name and signature. A typical shape for
        # this class of SDK is Init() -> int (0 on success), but this is
        # not confirmed against Mantra's own headers.
        init_fn = getattr(self._dll, "Init", None)
        if init_fn is not None:
            init_fn.restype = ctypes.c_int
            result = init_fn()
            if result != 0:
                raise DeviceError(f"MFS100 Init() returned {result} — device not ready.")

    def capture_template(self, timeout_ms: int) -> str:
        # VERIFY: real function name and signature. Guessed shape:
        #   int Capture(int timeout_ms, byte* image_buf, int* image_len)
        # A real implementation typically captures a raw image first and
        # extracts an ISO template as a second call (something like
        # MFS100ExtractISOTemplate) — this is written as a placeholder
        # that raises rather than fabricate a plausible-looking but wrong
        # binding, since a wrong ctypes signature here can crash the
        # process instead of raising a catchable Python exception.
        raise DeviceError(
            "Real MFS100 capture is not wired up yet — see VERIFY comments "
            "in device/mfs100.py. Set MFS100_MODE=mock to exercise the rest "
            "of this agent without hardware."
        )

    def match(self, template_a: str, template_b: str) -> int:
        # VERIFY: real function name and signature. Guessed shape:
        #   int MFS100MatchISO(byte* tmpl1, int len1, byte* tmpl2, int len2)
        # returning the 0-100000 score documented publicly.
        raise DeviceError(
            "Real MFS100 matching is not wired up yet — see VERIFY comments "
            "in device/mfs100.py."
        )

    @staticmethod
    def _decode(template_base64: str) -> bytes:
        return base64.b64decode(template_base64)

    @staticmethod
    def _encode(template_bytes: bytes) -> str:
        return base64.b64encode(template_bytes).decode("ascii")
