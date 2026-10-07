from typing import Literal
from urllib.parse import urlsplit

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
    app_name: str = "Koiwai"
    app_version: str = "0.0.1"
    environment: Literal["development", "production"] = "development"
    supabase_url: str = ""
    supabase_publishable_key: str = ""
    groq_api_key: SecretStr = SecretStr("")
    groq_model: str = "openai/gpt-oss-120b"
    groq_fallback_model: str = "openai/gpt-oss-20b"
    groq_timeout_seconds: int = Field(default=30, ge=5, le=45)
    groq_max_completion_tokens: int = Field(default=1536, ge=128, le=4096)

    @model_validator(mode="after")
    def validate_auth_config(self):
        if self.supabase_url:
            url = urlsplit(self.supabase_url)
            if (url.scheme != "https" or not url.hostname or url.username
                    or url.password or url.query or url.fragment
                    or url.path not in ("", "/")):
                raise ValueError("SUPABASE_URL deve ser a origem HTTPS do projeto.")
        if self.environment == "production" and not (
                self.supabase_url and self.supabase_publishable_key):
            raise ValueError("Configure Supabase Auth antes de iniciar em produção.")
        return self


settings = Settings()
