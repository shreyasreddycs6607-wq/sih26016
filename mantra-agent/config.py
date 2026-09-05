from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # The Bhoomimitra backend this agent reports match results to.
    backend_url: str = "http://localhost:8000"

    # This kiosk's key, in "selector.secret" form — issued once by an admin
    # via POST /admin/kiosks and pasted here. There is no way to recover a
    # lost key from the backend; a lost .env means re-registering the
    # kiosk. Never commit a real value — see .env.example.
    kiosk_key: str = ""

    # "mock" (default) simulates a scanner so the whole login pipeline can
    # be exercised without hardware — see device/mock.py's docstring for
    # exactly what it does and does not prove. "real" loads the actual
    # MFS100 SDK DLL, currently unverified — see device/mfs100.py.
    mfs100_mode: str = "mock"

    # How long a capture waits for a finger before giving up.
    capture_timeout_ms: int = 10_000

    # Which browser origins may call this agent. A kiosk normally serves
    # exactly one frontend, whether that's a local dev server during
    # testing or the deployed production URL once installed for real —
    # comma-separated so both can be allowed while developing this agent
    # itself. Unlike the backend's CORS setting, getting this wrong is a
    # usability problem, not a security one: everything this agent can be
    # made to do is already gated by the kiosk key and the backend's
    # username+nonce challenge, so a permissive origin list here doesn't
    # hand out anything a malicious page couldn't otherwise only get by
    # already controlling this exact machine.
    allowed_origins: str = "http://localhost:5173"

    port: int = 8791

    model_config = SettingsConfigDict(env_file=".env", case_sensitive=False)

    @property
    def cors_origins(self) -> list[str]:
        return [origin.strip() for origin in self.allowed_origins.split(",") if origin.strip()]


settings = Settings()
