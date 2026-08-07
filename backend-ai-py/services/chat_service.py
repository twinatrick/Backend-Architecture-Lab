import json
from collections.abc import AsyncIterator

import httpx

from config import settings


def chat(messages: list[dict], temperature: float | None = None) -> str:
    payload = {
        "model": settings.llm_model,
        "messages": messages,
        "temperature": temperature if temperature is not None else settings.llm_temperature,
        "stream": False,
    }
    resp = httpx.post(
        f"{settings.ollama_base_url.rstrip('/')}/api/chat",
        json=payload,
        timeout=300,
    )
    resp.raise_for_status()
    return resp.json().get("message", {}).get("content", "")


async def chat_stream(messages: list[dict], temperature: float | None = None) -> AsyncIterator[str]:
    payload = {
        "model": settings.llm_model,
        "messages": messages,
        "temperature": temperature if temperature is not None else settings.llm_temperature,
        "stream": True,
    }
    async with (
        httpx.AsyncClient(timeout=300) as client,
        client.stream(
            "POST",
            f"{settings.ollama_base_url.rstrip('/')}/api/chat",
            json=payload,
        ) as resp,
    ):
        resp.raise_for_status()
        async for line in resp.aiter_lines():
            if not line.strip():
                continue
            data = json.loads(line)
            delta = data.get("message", {}).get("content", "")
            if delta:
                yield delta
            if data.get("done"):
                break
