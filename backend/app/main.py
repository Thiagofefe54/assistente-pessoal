from backend.app.api.routes.chat import router as chat_router
from fastapi import FastAPI

from backend.app.api.routes.health import router as health_router
from backend.app.core.config import settings


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
)


@app.get("/")
async def root():
    return {
        "assistant": "online",
        "version": settings.app_version,
        "environment": settings.environment,
    }


app.include_router(
    health_router,
    prefix="/api/v1",
)

app.include_router(
    chat_router,
    prefix="/api/v1",
)