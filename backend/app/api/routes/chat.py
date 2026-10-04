from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, ConfigDict, Field

from backend.app.core.auth import current_user
from backend.app.core.config import settings

router = APIRouter(prefix="/chat", tags=["Chat"])


class ChatRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    message: str = Field(min_length=1, max_length=8000)


class ChatResponse(BaseModel):
    reply: str


def simulated_reply(request: ChatRequest) -> ChatResponse:
    message = request.message.strip()
    if not message:
        return ChatResponse(reply="Mestre, você não escreveu nada.")
    if message.lower() in {"oi", "olá", "ola"}:
        return ChatResponse(reply="Olá, Mestre. Estou aqui.")
    return ChatResponse(reply=f"Você disse: {message}")


@router.post("", response_model=ChatResponse)
async def chat(request: ChatRequest, user_id: UUID = Depends(current_user)):
    # The verified identity is available for future scoped context and AI calls.
    # This checkpoint deliberately keeps the existing simulated response.
    return simulated_reply(request)


@router.post("/demo", response_model=ChatResponse)
async def demo_chat(payload: ChatRequest, request: Request):
    if settings.environment != "development":
        raise HTTPException(404, "Não encontrado.")
    if request.headers.get("authorization"):
        raise HTTPException(400, "A demonstração não recebe credenciais.")
    return simulated_reply(payload)


@router.get("/me")
async def me(user_id: UUID = Depends(current_user)):
    return {"user_id": str(user_id)}
