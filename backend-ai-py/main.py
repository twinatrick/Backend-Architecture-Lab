import asyncio
import socket

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from config import settings
from routers import chat, stt, tts

app = FastAPI(title="AI Python Sidecar", version="1.0.0")

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


@app.on_event("startup")
async def startup():
    _nacos_register()


@app.on_event("shutdown")
async def shutdown():
    _nacos_deregister()


@app.get("/health")
async def health():
    return {"status": "ok"}


_nacos_service = None


def _get_local_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.254.254.254", 1))
        ip = s.getsockname()[0]
    except Exception:
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip


def _nacos_register():
    global _nacos_service
    if not settings.nacos_server_addr:
        return
    try:
        from nacos import NacosClient
        client = NacosClient(settings.nacos_server_addr, namespace=settings.nacos_namespace)
        ip = _get_local_ip()
        client.add_naming_instance(
            settings.service_name,
            ip,
            settings.server_port,
            ephemeral=True,
        )
        _nacos_service = client
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
    uvicorn.run("main:app", host="0.0.0.0", port=settings.server_port, reload=True)
