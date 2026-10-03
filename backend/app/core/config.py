from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    app_name: str = "Assistente Pessoal"
    app_version: str = "0.0.1"
    environment: str = "development"


settings = Settings()