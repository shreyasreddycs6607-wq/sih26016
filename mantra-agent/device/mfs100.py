"""The real Mantra MFS100 device, via the vendor's own MFS100 Client Service.

**Verified against the actual scanner** — see the investigation this file was
rewritten from: `MFS100Driver_9.2.0.0` installs three pieces on the kiosk PC,
not just a driver:

- `MFS100.sys` — the kernel-mode USB driver, so Windows recognises the
  scanner at all (Device Manager shows it as "MFS100", VID_2C0F&PID_1005).
- `MFS100ClientSvc` — a background Windows service (`MFS100ClientWinSvc.exe`)
  that talks to the DLL/driver directly and exposes a small local REST API
  over HTTP, self-hosted at `http://127.0.0.1:8004/mfs100/` (and a secure
  variant on :8003 with a self-signed cert Windows won't trust by default —
  the plain HTTP port is what Mantra's own sample code uses, and what this
  file uses).
- A Mantra RDService install for Aadhaar/UIDAI mode, unrelated to this file
  — see mantra-agent/README.md for why that mode is the wrong one.

This is a much smaller and more reliable integration surface than binding
the SDK's native DLL with ctypes (this file's previous approach, which
guessed at function signatures nobody had verified against real hardware):
the Client Service already speaks plain JSON over HTTP, is running the
instant Windows starts (it's an auto-start service, not something this
agent has to load in-process), and is exactly what Mantra ships a browser
JS sample for (`MFS100ClientService/Test/mfs100-9.0.2.6.js`, installed
alongside the service) — confirming this is the vendor's own intended
integration path, not a workaround.

Confirmed live against the physical scanner during development:
- `GET .../info` returns `{ErrorCode, ErrorDescription, DeviceInfo: {SerialNo,
  Model, Make, ...}}` — `ErrorCode` is a *string* ("0" for success).
- `POST .../capture {Quality, TimeOut}` returns `{ErrorCode,
  ErrorDescription, Quality, Nfiq, IsoTemplate, AnsiTemplate, BitmapData,
  ...}` on success — `IsoTemplate` is exactly the base64 ISO/ANSI-378
  template this project stores and forwards.
- `POST .../verify {ProbTemplate, GalleryTemplate, BioType}` returns
  `{ErrorCode, ErrorDescription, Status}` — `Status` is a **boolean**, not
  a numeric score. Mantra's local matcher makes its own accept/reject
  decision and only reports the verdict; it does not expose the raw
  MFS100MatchISO score over this API. See `match()` below for how that
  boolean is mapped onto the 0-100000 range
  app.routers.biometrics.MIN_FINGERPRINT_MATCH_SCORE expects.
"""

import httpx

from device.base import DeviceError

# Mantra's own sample code (and the Test page installed with the Client
# Service) uses the plain-HTTP port. The secure :8003 port uses a
# self-signed certificate that neither this agent nor a browser trusts by
# default — switching to it would need that certificate installed first,
# which buys nothing for a same-machine loopback call no attacker can
# intercept without already controlling this exact kiosk.
_BASE_URL = "http://127.0.0.1:8004/mfs100"

# A boolean-only match gets mapped onto the numeric range the backend
# checks against MIN_FINGERPRINT_MATCH_SCORE (14000) with the same two
# constants the mock device already uses, so a passing score here reads
# identically to a passing mock score in any log or audit trail.
_MATCH_SCORE = 100_000
_NO_MATCH_SCORE = 0


class MFS100Device:
    label = "mfs100"

    def __init__(self) -> None:
        # Deliberately does not probe the scanner here: the Client Service
        # itself is a Windows auto-start service, independent of this
        # agent's process, so "is the scanner plugged in right now" can
        # change at any moment this agent is running — a landowner walking
        # up to an idle kiosk, or an officer unplugging the scanner between
        # logins. Failing agent startup over a transient disconnect would
        # take down capture/enroll for everyone, not just fingerprint login.
        # See health() for the live check the frontend polls instead.
        self._client = httpx.Client(base_url=_BASE_URL, timeout=15.0)

    def health(self) -> dict:
        """Live connectivity check — never cached, since the whole point is
        answering "is the scanner attached right now", not "was it attached
        when this agent started". Returns {connected, detail, device} —
        `device` (serial/model) is only present when connected, per
        BiometricEnrollResponse's project-wide rule of not handing back more
        than the caller needs."""
        try:
            info = self._client.get("/info").json()
        except httpx.RequestError:
            return {
                "connected": False,
                "detail": "Mantra MFS100 Client Service is not reachable at "
                f"{_BASE_URL}. Is MFS100ClientSvc running?",
            }

        if str(info.get("ErrorCode")) != "0":
            return {
                "connected": False,
                "detail": info.get("ErrorDescription") or "Scanner not ready.",
            }

        device_info = info.get("DeviceInfo") or {}
        return {
            "connected": True,
            "detail": "Scanner connected.",
            "device": {
                "model": device_info.get("Model"),
                "serial_no": device_info.get("SerialNo"),
            },
        }

    def capture_template(self, timeout_ms: int) -> str:
        # The service's TimeOut is in whole seconds; Mantra documents
        # 10-60 as the sane range (0 means "wait forever", which this
        # agent never wants — a kiosk login attempt must eventually give
        # up and let the person retry).
        timeout_s = max(10, min(60, round(timeout_ms / 1000)))
        try:
            resp = self._client.post(
                "/capture", json={"Quality": 60, "TimeOut": timeout_s}
            )
            data = resp.json()
        except httpx.RequestError as exc:
            raise DeviceError(
                "Lost contact with the Mantra MFS100 Client Service during "
                "capture. Check the scanner is still connected."
            ) from exc

        error_code = str(data.get("ErrorCode"))
        if error_code != "0":
            raise DeviceError(_describe_capture_error(error_code, data.get("ErrorDescription")))

        template = data.get("IsoTemplate")
        if not template:
            raise DeviceError("The scanner reported success but returned no fingerprint template.")
        return template

    def match(self, template_a: str, template_b: str) -> int:
        try:
            resp = self._client.post(
                "/verify",
                json={
                    "ProbTemplate": template_a,
                    "GalleryTemplate": template_b,
                    "BioType": "FMR",
                },
            )
            data = resp.json()
        except httpx.RequestError as exc:
            raise DeviceError(
                "Lost contact with the Mantra MFS100 Client Service while matching."
            ) from exc

        if str(data.get("ErrorCode")) != "0":
            raise DeviceError(
                f"Fingerprint match failed: {data.get('ErrorDescription') or 'unknown error'}"
            )

        return _MATCH_SCORE if data.get("Status") else _NO_MATCH_SCORE


def _describe_capture_error(error_code: str, description: str | None) -> str:
    # Mantra's own ErrorDescription is sometimes already readable ("Device
    # Not Found", "Finger Not Placed Properly") and sometimes a single bare
    # word ("Timeout") that reads as a stack trace rather than an
    # instruction — worth a specific, actionable message instead of
    # whatever string the service happens to send.
    lower = (description or "").lower()
    if "timeout" in lower or "time out" in lower:
        return "No finger was presented in time. Try again and hold your finger steady on the scanner."
    if error_code in {"-1000", "1000"} or "not found" in lower:
        return "No fingerprint scanner found. Check it's plugged in and the MFS100 driver is installed."
    if description:
        return description
    return f"Fingerprint capture failed (error {error_code})."
