"""The Mantra kiosk agent.

Runs on the kiosk PC itself, alongside the Mantra MFS100 driver — never on
the same host as the Bhoomimitra backend, and never reachable from outside
this machine (see config.py's allowed_origins for why that's still true
even though it accepts browser requests). See README.md for installation
and mantra-agent's role in the wider login flow.

Two endpoints:

POST /login   — the whole fingerprint-login round trip. Fetches the
  claimed user's enrolled template from the backend, captures a fresh one
  from the attached scanner, matches them locally (this is the whole
  reason this agent exists — see
  sih26016-backend/app/services/kiosk_auth.py's docstring for why the
  backend itself cannot do this step), and reports the score back for the
  backend to decide on. Returns the backend's session on success.

POST /capture — one raw capture, for enrollment. The browser forwards
  whatever this returns straight to the authenticated
  POST /biometrics/fingerprint/enroll on the backend; this agent never
  talks to that endpoint itself; enrollment is something the signed-in
  officer's own browser session does, not something to run.
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

import backend_client
from config import settings
from device import DeviceError, get_device

app = FastAPI(title="Bhoomimitra Mantra Kiosk Agent")

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_methods=["POST"],
    allow_headers=["Content-Type"],
)

_device = get_device(settings.mfs100_mode)


class LoginRequest(BaseModel):
    username: str


@app.get("/health")
def health():
    # A live probe, not just "did this process start" — the frontend polls
    # this to show Scanner Connected / Not Connected, which has to reflect
    # whether the scanner is attached right now, not whether it was
    # attached whenever this agent last restarted.
    device_health = _device.health()
    return {
        "status": "ok",
        "device_label": _device.label,
        "mode": settings.mfs100_mode,
        **device_health,
    }


@app.post("/login")
async def login(payload: LoginRequest):
    if not settings.kiosk_key:
        raise HTTPException(500, "This agent has no kiosk key configured — see .env.example.")

    try:
        challenge = await backend_client.fetch_challenge(payload.username)
    except backend_client.BackendError as exc:
        raise HTTPException(404, str(exc)) from exc

    try:
        fresh_template = _device.capture_template(settings.capture_timeout_ms)
    except DeviceError as exc:
        raise HTTPException(422, str(exc)) from exc

    score = _device.match(challenge["template_base64"], fresh_template)

    try:
        return await backend_client.report_match(challenge["challenge_nonce"], score)
    except backend_client.BackendError as exc:
        raise HTTPException(401, str(exc)) from exc


@app.post("/capture")
def capture():
    """One raw capture for enrollment — no backend call, no username, no
    matching. What the officer's already-authenticated browser session
    does with the result is POST /biometrics/fingerprint/enroll's job, not
    this agent's."""
    try:
        template = _device.capture_template(settings.capture_timeout_ms)
    except DeviceError as exc:
        raise HTTPException(422, str(exc)) from exc
    return {"template_base64": template}


if __name__ == "__main__":
    import uvicorn

    # 127.0.0.1 only, deliberately — this agent should never be reachable
    # from anywhere but the browser running on this same kiosk.
    uvicorn.run(app, host="127.0.0.1", port=settings.port)
