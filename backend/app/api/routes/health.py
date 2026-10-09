from fastapi import APIRouter
from backend.app.core.config import settings

router = APIRouter(
    prefix="/health",
    tags=["Health"],
)


@router.get("")
async def health():
    return {
        "status": "ok",
        "ai_provider": settings.ai_provider,
    }
