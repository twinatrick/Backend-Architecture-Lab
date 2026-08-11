import logging
import os

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"
import socket
import threading
import time
from contextlib import asynccontextmanager

import httpx
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

try:
    from nacos import NacosClient
except ImportError:
    NacosClient = None

from config import settings
from routers import chat
from routers import stt
from routers import tts

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    _nacos_register()
    await _warmup_ollama()
    yield
    _nacos_deregister()


app = FastAPI(title="AI Python Sidecar", version="1.0.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(stt.router)
app.include_router(tts.router)
app.include_router(chat.router)


@app.get("/health")
async def health():
    return {"status": "ok"}


async def _warmup_ollama():
    try:
        async with httpx.AsyncClient(timeout=60.0) as client:
            await client.post(
                f"{settings.ollama_base_url.rstrip('/')}/api/chat",
                json={
                    "model": settings.llm_model,
                    "messages": [{"role": "user", "content": "hello"}],
                    "stream": False,
                },
            )
    except Exception as exc:
        logger.warning("[Ollama] 暖機請求失敗，稍後正式呼叫時再重試: %s", exc)


_nacos_service = None


def _get_local_ip() -> str:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("10.254.254.254", 1))
        ip = sock.getsockname()[0]
    except OSError:
        ip = "127.0.0.1"
    finally:
        sock.close()
    return ip


def _nacos_register():
    global _nacos_service
    if not settings.nacos_server_addr:
        return
    try:
        if NacosClient is None:
            logger.warning("[nacos] NacosClient 不可用，跳過服務註冊")
            return
        client = NacosClient(settings.nacos_server_addr, namespace=settings.nacos_namespace)
        ip = _get_local_ip()
        client.add_naming_instance(
            settings.service_name,
            ip,
            settings.server_port,
            ephemeral=True,
        )
        _nacos_service = client
        print(f"[nacos] registered {settings.service_name}@{ip}:{settings.server_port}")

        def _heartbeat():
            while True:
                time.sleep(5)
                try:
                    client.send_heartbeat(settings.service_name, ip, settings.server_port)
                except Exception as exc:
                    logger.warning("[nacos] heartbeat 失敗: %s", exc)

        heartbeat_thread = threading.Thread(target=_heartbeat, daemon=True)
        heartbeat_thread.start()
    except Exception as e:
        print(f"[nacos] register failed: {e}")


def _nacos_deregister():
    global _nacos_service
    if _nacos_service is None:
        return
    try:
        ip = _get_local_ip()
        _nacos_service.remove_naming_instance(
            settings.service_name,
            ip,
            settings.server_port,
        )
    except Exception as e:
        print(f"[nacos] deregister failed: {e}")


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=settings.server_port)
