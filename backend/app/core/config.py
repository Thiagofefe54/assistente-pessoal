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
    ai_provider: Literal['groq', 'poe'] = 'groq'
    poe_api_key: SecretStr = SecretStr('')
    poe_model: str = 'GPT-OSS-120B'
    poe_vision_model: str = 'GPT-4.1-mini'
    poe_web_model: str = 'GPT-4.1-mini'
    poe_max_output_tokens: int = Field(default=1536, ge=128, le=4096)
    groq_api_key: SecretStr = SecretStr("")
    groq_model: str = "openai/gpt-oss-120b"
    groq_vision_model: str = "qwen/qwen3.8-27b"
    groq_fallback_model: str = "openai/gpt-oss-20b"
    groq_timeout_seconds: int = Field(default=30, ge=5, le=45)
    groq_max_completion_tokens: int = Field(default=1536, ge=128, le=4096)
    # Personal read-only connection: disabled until credentials and consent are ready.
    pluggy_enabled: bool = False
    pluggy_owner_id: str = ''
    pluggy_client_id: SecretStr = SecretStr('')
    pluggy_client_secret: SecretStr = SecretStr('')
    pluggy_item_ids: SecretStr = SecretStr('')
    google_enabled: bool = False
    google_client_id: str = ''
    google_client_secret: SecretStr = SecretStr('')
    google_redirect_uri: str = ''
    connections_encryption_key: SecretStr = SecretStr('')
    connections_supabase_secret_key: SecretStr = SecretStr('')

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
