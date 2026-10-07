from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from backend.app.core.auth import current_user
from backend.app.core.config import settings
from backend.app.core.ai import reply

router = APIRouter(prefix="/chat", tags=["Chat"])


class ContextMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)


class ChatRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    message: str = Field(min_length=1, max_length=8000)
    history: list[ContextMessage] = Field(default_factory=list, max_length=20)

    @field_validator("message")
    @classmethod
    def nonblank_message(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("Escreva uma mensagem.")
        return value.strip()

    @model_validator(mode="after")
    def bounded_context(self):
        if sum(len(item.content) for item in self.history) > 12000:
            raise ValueError("O contexto recente excedeu o limite.")
        return self


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
def chat(request: ChatRequest, user_id: UUID = Depends(current_user)):
    # Auth runs before inference. The account-scoped Android database provides
    # untrusted recent context; this route never reads another user's memory.
    return ChatResponse(reply=reply(request.message, [item.model_dump() for item in request.history]))


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
