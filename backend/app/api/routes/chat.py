from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from backend.app.core.auth import current_user
from backend.app.core.config import settings
from backend.app.core.memory import confirmed_facts
from backend.app.core.tasks import task_context, TaskProposal
from backend.app.core.conversation import converse
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
from backend.app.core.actions import existing_action, direct_conversation, digest, undo_action

router = APIRouter(prefix="/chat", tags=["Chat"])


class ContextMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)


class ChatRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    message: str = Field(min_length=1, max_length=8000)
    history: list[ContextMessage] = Field(default_factory=list, max_length=20)
    timezone: str = Field(default='America/Sao_Paulo', min_length=1, max_length=100)
    request_id: UUID | None = None
    task_mode: Literal['review','direct'] = 'review'

    @field_validator('timezone')
    @classmethod
    def valid_timezone(cls, value: str) -> str:
        try:
            ZoneInfo(value)
        except (ValueError, ZoneInfoNotFoundError):
            raise ValueError('Fuso horário inválido.') from None
        return value

    @field_validator("message")
    @classmethod
    def nonblank_message(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("Escreva uma mensagem.")
        return value.strip()

    @model_validator(mode="after")
    def bounded_context(self):
        if self.task_mode=='direct' and self.request_id is None:
            raise ValueError('Identidade do pedido obrigatória.')
        if sum(len(item.content) for item in self.history) > 12000:
            raise ValueError("O contexto recente excedeu o limite.")
        return self


class ChatResponse(BaseModel):
    reply: str
    task_draft: TaskProposal | None = None
    action_receipt: dict | None = None


def simulated_reply(request: ChatRequest) -> ChatResponse:
    message = request.message.strip()
    if not message:
        return ChatResponse(reply="Mestre, você não escreveu nada.")
    if message.lower() in {"oi", "olá", "ola"}:
        return ChatResponse(reply="Olá, Mestre. Estou aqui.")
    return ChatResponse(reply=f"Você disse: {message}")


@router.post("", response_model=ChatResponse)
def chat(payload: ChatRequest, request: Request, user_id: UUID = Depends(current_user)):
    # Owner comes from validated Auth, never from the body. RLS also protects reads.
    authorization=request.headers['authorization']
    if payload.task_mode=='direct':
        previous=existing_action(user_id,authorization,payload.request_id,digest(payload.message,payload.timezone))
        if previous: return ChatResponse(**previous)
    facts = confirmed_facts(user_id, authorization)
    tasks = task_context(user_id, request.headers['authorization'], payload.timezone, payload.message)
    history=[item.model_dump() for item in payload.history]
    if payload.task_mode=='direct':
        return ChatResponse(**direct_conversation(payload.message,history,facts,tasks,user_id,authorization,payload.request_id,payload.timezone))
    return ChatResponse(**converse(payload.message, history, facts, tasks))


@router.post('/actions/{request_id}/undo',response_model=ChatResponse)
def undo(request_id:UUID,request:Request,user_id:UUID=Depends(current_user)):
    return undo_action(user_id,request.headers['authorization'],request_id)


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
