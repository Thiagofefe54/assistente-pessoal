from backend.app.api.routes.chat import router as chat_router
from fastapi import FastAPI

from backend.app.api.routes.health import router as health_router
from backend.app.core.config import settings
from backend.app.api.routes.journal import router as journal_router
from backend.app.api.routes.reports import router as reports_router
from backend.app.api.routes.assistant import router as assistant_router
from backend.app.api.routes.connections import router as connections_router
from backend.app.core.privacy import PrivateApiResponses


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
app.add_middleware(PrivateApiResponses)
app.include_router(journal_router, prefix="/api/v1")
app.include_router(reports_router, prefix="/api/v1")
app.include_router(assistant_router, prefix="/api/v1")
app.include_router(connections_router, prefix="/api/v1")
