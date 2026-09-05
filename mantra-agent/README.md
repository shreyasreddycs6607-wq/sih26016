# Bhoomimitra kiosk agent

A small local service that runs on an officer kiosk PC — the one place in
Bhoomimitra a Mantra MFS100 fingerprint scanner is physically attached — and
lets the browser's "unlock through fingerprint" login option talk to it.

**Why this exists as a separate process at all**, rather than the backend
just handling fingerprint login itself: the Bhoomimitra backend runs in
Docker on a server nowhere near the officer's desk. Only this kiosk's own
machine can reach the scanner's driver, so *something* running on that exact
machine has to do the capture-and-match step and report the result. See
`sih26016-backend/app/services/kiosk_auth.py`'s docstring for the fuller
reasoning, and `sih26016-backend/app/routers/biometrics.py` for what it
reports to and what the backend does with that report.

## What it is not

It is not an Aadhaar/UIDAI integration. Mantra devices also support a
"Registered Device" mode built for Aadhaar authentication, whose captured
data is encrypted so that only UIDAI's own servers can read it — completely
unusable for a local "does this match the fingerprint on file" check, and
going through UIDAI for real requires an AUA/KUA license this project does
not have. This agent uses the device's other, non-Aadhaar SDK mode, which
returns a plain ISO/ANSI-378 template meant for exactly this kind of local
matching.

## Running it today, without the scanner

```
cd mantra-agent
python -m venv .venv && .venv\Scripts\activate      # Windows
pip install -r requirements.txt
copy .env.example .env
# fill in BACKEND_URL and KIOSK_KEY (see below) — leave MFS100_MODE=mock
python main.py
```

With `MFS100_MODE=mock` (the default), every "scan" returns the same fixed
fake template. That's enough to exercise the **entire** fingerprint login
pipeline end to end — enroll once, then log in — without a real scanner in
the room. It proves nothing about actual fingerprint matching; it proves the
plumbing between browser, agent, and backend is wired correctly. See
`device/mock.py`'s docstring.

## Getting a kiosk key

An admin runs, once, against the running backend:

```
POST /admin/kiosks
{ "label": "DC Office Bengaluru, Counter 1", "district_id": 1 }
```

(Any account with the `admin` role — via `POST /admin/kiosks` — is `admin`,
authenticated the normal way with a Bearer token.) The response's `key`
field is shown exactly once and cannot be recovered afterwards. Paste it
into this agent's `.env` as `KIOSK_KEY`. If it's lost, revoke the kiosk
(`POST /admin/kiosks/{id}/revoke`) and register a new one.

## Switching to real hardware

`device/mfs100.py` talks to the Mantra MFS100 Client Service, not the SDK's
native DLL directly — see that file's own docstring for why. That service is
installed and started automatically as part of `MFS100Driver_x.x.x.x.exe`,
the one installer Mantra ships for this scanner; there is no separate SDK
download to go find.

1. Run `MFS100Driver_x.x.x.x.exe` on the kiosk PC and plug in the scanner.
   The installer sets up three things, all visible afterward: the
   `MFS100.sys` USB driver (Device Manager shows the scanner as "MFS100"),
   the `MFS100ClientSvc` Windows service (self-hosts a local REST API on
   `http://127.0.0.1:8004/mfs100/`), and a Mantra RDService install for
   Aadhaar/UIDAI mode that this project doesn't use.
2. Confirm the Client Service is actually running and can see the scanner —
   open `C:\Program Files\Mantra\MFS100\MFS100ClientService\Test\MFS100ClientServiceTest.htm`
   directly in a browser (no server needed) and click **Get Info**. It
   should show a serial number, make and model. If it doesn't, this agent's
   `real` mode won't work either — that test page and this agent hit the
   exact same local service.
3. Set `MFS100_MODE=real` in `.env` and start this agent. `GET /health`
   should report `"connected": true` with the scanner's model and serial.
4. Re-test the whole enroll-then-login pipeline exactly as under mock mode
   — enroll a finger through the officer's authenticated Security page,
   then sign in with it at `/login`'s fingerprint option.

## Why CORS here is more permissive than the backend's

`config.py`'s `ALLOWED_ORIGINS` accepts whatever frontend origin you list,
with none of the production hardening `sih26016-backend/app/config.py`
enforces for its own CORS setting. That's deliberate, not an oversight:
everything this agent can be made to do is already gated by the kiosk key
and the backend's per-attempt nonce (see `biometrics.py`), so a browser page
that isn't the real Bhoomimitra frontend calling this agent doesn't gain
anything a person already sitting at this exact kiosk couldn't do anyway.

## Endpoints

```
GET  /health           -> {status, device, mode}
POST /login             {username} -> backend's {access_token, token_type, user}
POST /capture           {} -> {template_base64}, for enrollment only
```

Bound to `127.0.0.1` only — never reachable from outside this machine.
