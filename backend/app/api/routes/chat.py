from fastapi import APIRouter
from pydantic import BaseModel


router = APIRouter(
    prefix="/chat",
    tags=["Chat"],
)


class ChatRequest(BaseModel):
    message: str


class ChatResponse(BaseModel):
    reply: str


@router.post("", response_model=ChatResponse)
async def chat(request: ChatRequest):
    message = request.message.strip()

    if not message:
        return ChatResponse(
            reply="Mestre, você não escreveu nada."
        )

    if message.lower() in {"oi", "olá", "ola"}:
        return ChatResponse(
            reply="Olá, Mestre. Estou aqui."
        )

    return ChatResponse(
        reply=f"Você disse: {message}"
    )