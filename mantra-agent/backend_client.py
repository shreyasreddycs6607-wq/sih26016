"""Everything this agent says to the Bhoomimitra backend.

Three calls, all carrying the kiosk's X-Kiosk-Key header — see
sih26016-backend/app/services/kiosk_auth.py for what authenticates it and
sih26016-backend/app/routers/biometrics.py for what each endpoint does.
"""

import httpx

from config import settings


class BackendError(Exception):
    """The backend rejected the request or couldn't be reached — always
    carries a message safe to show the person standing at the kiosk."""


def _headers() -> dict:
    return {"X-Kiosk-Key": settings.kiosk_key}


async def fetch_challenge(username: str) -> dict:
    """{challenge_nonce, template_base64, expires_in_seconds}, or raises
    BackendError — most commonly because the username has no fingerprint
    enrolled, which the backend deliberately reports identically to "no
    such user" (see FingerprintChallengeRequest's docstring)."""
    async with httpx.AsyncClient(timeout=10.0) as client:
        try:
            resp = await client.post(
                f"{settings.backend_url}/biometrics/fingerprint/challenge",
                json={"username": username},
                headers=_headers(),
            )
        except httpx.RequestError as exc:
            raise BackendError(
                "Could not reach the Bhoomimitra server. Check this kiosk's network connection."
            ) from exc

    if resp.status_code != 200:
        raise BackendError(_detail(resp) or "That username has no fingerprint on file.")
    return resp.json()


async def report_match(challenge_nonce: str, score: int) -> dict:
    """{access_token, token_type, user} on a genuine match, or raises
    BackendError — the backend re-checks `score` against its own threshold
    rather than trusting this agent's own pass/fail verdict, so a bug here
    that always reports success still can't mint a session on its own."""
    async with httpx.AsyncClient(timeout=10.0) as client:
        try:
            resp = await client.post(
                f"{settings.backend_url}/biometrics/fingerprint/login",
                json={"challenge_nonce": challenge_nonce, "score": score},
                headers=_headers(),
            )
        except httpx.RequestError as exc:
            raise BackendError(
                "Could not reach the Bhoomimitra server. Check this kiosk's network connection."
            ) from exc

    if resp.status_code != 200:
        raise BackendError(_detail(resp) or "Fingerprint not recognised.")
    return resp.json()


def _detail(resp: httpx.Response) -> str | None:
    try:
        body = resp.json()
    except ValueError:
        return None
    detail = body.get("detail")
    return detail if isinstance(detail, str) else None
