from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from config import settings
from services.chat_service import chat
from services.chat_service import chat_stream

router = APIRouter()


class ChatRequest(BaseModel):
    messages: list[dict]
    temperature: float | None = None
    stream: bool = False


@router.post("/chat")
async def chat_endpoint(body: ChatRequest):
    if body.stream:
        return StreamingResponse(
            chat_stream(body.messages, body.temperature),
            media_type="text/event-stream",
        )
    content = chat(body.messages, body.temperature)
    return {"content": content, "model": settings.llm_model}
